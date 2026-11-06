package ch.fhnw.digibp.classroom.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.cibseven.connect.ai.agent.AgentConnectorConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class AiModelService {
    private static final Logger LOG = LoggerFactory.getLogger(AiModelService.class);
    private final ObjectProvider<Modeler> modeler;
    private final Environment environment;
    private final ObjectMapper json;
    private String defaultModel;
    private List<String> models;

    public AiModelService(ObjectProvider<Modeler> modeler, Environment environment, ObjectMapper json) {
        this.modeler = modeler;
        this.environment = environment;
        this.json = json;
        defaultModel = validModel(environment.getProperty("CIBSEVEN_CONNECT_AI_AGENT_DEFAULT_MODEL", "gpt-5.4-nano"));
        models = List.of(defaultModel);
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void initialize() {
        apply(defaultModel, models);
        try {
            reload();
        } catch (ResponseStatusException exception) {
            // reload() already logged the failure; startup keeps the configured default.
        }
    }

    public synchronized List<String> reload() {
        HttpURLConnection connection = null;
        try {
            String base = environment.getProperty("OPENAI_BASE_URL", "");
            if (base.isBlank()) {
                throw new IllegalArgumentException("No model endpoint configured");
            }
            String url = base.replaceAll("/+$", "").replaceFirst("/v1$", "") + "/model/info";
            URI uri = URI.create(url);
            String host = uri.getHost();
            boolean local = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "[::1]".equals(host);
            if (host == null || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                    || !("https".equals(uri.getScheme()) || (local && "http".equals(uri.getScheme())))) {
                throw new IllegalArgumentException("Model endpoint must use HTTPS or local HTTP");
            }
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json");
            String key = environment.getProperty("OPENAI_API_KEY", "");
            if (!key.isBlank()) {
                connection.setRequestProperty("Authorization", "Bearer " + key);
            }
            if (connection.getResponseCode() != 200) {
                throw new IllegalStateException("Model endpoint returned an error");
            }
            byte[] body;
            try (var stream = connection.getInputStream()) {
                body = stream.readNBytes(2 * 1024 * 1024 + 1);
            }
            if (body.length > 2 * 1024 * 1024) {
                throw new IllegalArgumentException("Model response too large");
            }
            var root = json.readTree(body);
            var data = root == null ? null : root.get("data");
            if (data == null || !data.isArray()) {
                throw new IllegalArgumentException("Expected a data array");
            }
            var loaded = new LinkedHashSet<String>();
            loaded.add(defaultModel);
            for (var item : data) {
                var mode = item.path("model_info").path("mode");
                var name = item.path("model_name");
                if ((mode.isMissingNode() || mode.isNull() || "chat".equals(mode.asText())) && name.isTextual()) {
                    loaded.add(validModel(name.textValue()));
                }
            }
            apply(defaultModel, List.copyOf(loaded));
            return models;
        } catch (Exception exception) {
            // Do not expose provider bodies, credentials or exception messages.
            LOG.warn("AI model fetch failed ({}); keeping current models.", exception.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI model fetch failed");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public synchronized List<String> models() {
        return models;
    }

    public synchronized String setDefault(String value) {
        String model = validModel(value);
        var available = new LinkedHashSet<>(models);
        available.add(model);
        apply(model, List.copyOf(available));
        return model;
    }

    private void apply(String model, List<String> available) {
        Modeler integration = modeler.getIfAvailable();
        if (integration != null) {
            integration.configure(model, available);
        }
        System.setProperty(AgentConnectorConstants.DEFAULT_MODEL_PROPERTY, model);
        // Publish local state only after the modeler and connector update succeeded.
        defaultModel = model;
        models = available;
    }

    private static String validModel(String model) {
        if (model == null || model.isBlank() || model.length() > 512 || model.chars().anyMatch(Character::isISOControl)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid model ID");
        }
        return model.trim();
    }

    /** EE supplies the modeler adapter; the fetcher and connector default also work in Community. */
    public interface Modeler {
        void configure(String defaultModel, List<String> models);
    }
}
