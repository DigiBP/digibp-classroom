package ch.fhnw.digibp.classroom.config;

import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.StandardCookieSpec;
import org.apache.hc.core5.http.HttpHeaders;
import org.cibseven.bpm.engine.ProcessEngineException;
import org.cibseven.bpm.engine.impl.context.Context;
import org.cibseven.bpm.engine.variable.value.TypedValue;
import org.cibseven.bpm.model.bpmn.instance.cibseven.CamundaProperties;
import org.cibseven.connect.spi.ConnectorInvocation;
import org.cibseven.connect.spi.ConnectorRequestInterceptor;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.util.Set;

/** Reads credentials from server configuration, BPMN properties, or legacy transient variables. */
public class HttpAuthenticationInterceptor implements ConnectorRequestInterceptor {
    public static final String AUTH_MODE = "digibpAuthMode";
    public static final String KEY_VARIABLE = "digibpAuthKeyVariable";
    public static final String MODEL_KEY = "digibpAuthKey";

    private final Environment environment;

    public HttpAuthenticationInterceptor(Environment environment) {
        this.environment = environment;
    }

    @Override
    public Object handleInvocation(ConnectorInvocation invocation) throws Exception {
        Object mode = invocation.getRequest().getRequestParameter(AUTH_MODE);
        if (mode == null) {
            // Existing HTTP connector requests keep their behavior unless they opt in.
            return invocation.proceed();
        }
        if (!(mode instanceof String) || !Set.of("none", "own-key", "transient", "server-openai").contains(mode)) {
            throw new ProcessEngineException("Unsupported HTTP authentication mode");
        }
        if (!(invocation.getTarget() instanceof HttpUriRequestBase request)) {
            throw new ProcessEngineException("Unsupported HTTP request implementation for managed authentication");
        }
        URI url = request.getUri();
        if (url.getRawUserInfo() != null || url.getRawFragment() != null) {
            throw new ProcessEngineException("Managed HTTP requests cannot contain URL credentials or fragments");
        }
        request.removeHeaders(HttpHeaders.AUTHORIZATION);
        request.removeHeaders(HttpHeaders.PROXY_AUTHORIZATION);
        request.removeHeaders(HttpHeaders.COOKIE);
        RequestConfig previous = request.getConfig();
        request.setConfig(RequestConfig.copy(previous == null ? RequestConfig.DEFAULT : previous)
                .setRedirectsEnabled(false).setAuthenticationEnabled(false)
                .setCookieSpec(StandardCookieSpec.IGNORE).build());
        if ("none".equals(mode)) {
            return invocation.proceed();
        }
        if (!"https".equalsIgnoreCase(url.getScheme()) || url.getHost() == null) {
            throw new ProcessEngineException("Bearer authentication requires an absolute HTTPS URL");
        }
        if (!Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS").contains(request.getMethod())) {
            // In particular, TRACE may echo request headers back to the process.
            throw new ProcessEngineException("This HTTP method is not allowed with Bearer authentication");
        }
        String key;
        if ("server-openai".equals(mode)) {
            String baseUrl = environment.getProperty("OPENAI_BASE_URL", "").replaceAll("/+$", "");
            String destination = url.normalize().toString();
            if (baseUrl.isBlank() || !(destination.equals(baseUrl)
                    || destination.startsWith(baseUrl + "/") || destination.startsWith(baseUrl + "?"))) {
                throw new ProcessEngineException("Server key may only be sent to OPENAI_BASE_URL or its endpoints");
            }
            // Use the host from the request URI instead of a model-supplied Host header.
            request.removeHeaders(HttpHeaders.HOST);
            key = environment.getProperty("OPENAI_API_KEY");
        } else if ("own-key".equals(mode)) {
            var context = Context.getBpmnExecutionContext();
            if (context == null) {
                throw new ProcessEngineException("Own key authentication requires BPMN execution");
            }
            var extensions = context.getExecution().getBpmnModelElementInstance().getExtensionElements();
            key = null;
            if (extensions != null) {
                for (var properties : extensions.getElementsQuery().filterByType(CamundaProperties.class).list()) {
                    for (var property : properties.getCamundaProperties()) {
                        if (MODEL_KEY.equals(property.getCamundaName())) {
                            key = property.getCamundaValue();
                        }
                    }
                }
            }
        } else {
            Object name = invocation.getRequest().getRequestParameter(KEY_VARIABLE);
            var context = Context.getBpmnExecutionContext();
            if (!(name instanceof String variableName) || variableName.isBlank() || context == null) {
                throw new ProcessEngineException("Transient authentication requires a runtime key variable and BPMN execution");
            }
            TypedValue value = context.getExecution().getVariableTyped(variableName);
            if (value == null || !value.isTransient() || !(value.getValue() instanceof String)) {
                throw new ProcessEngineException("The runtime API key must be a transient String variable");
            }
            key = (String) value.getValue();
        }
        if (key == null || key.isEmpty() || !key.matches("[\\x21-\\x7E]+")) {
            // Do not include key values in validation errors, including invalid header characters.
            throw new ProcessEngineException("The selected API key is missing or is not a valid Bearer token");
        }
        request.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + key);
        try {
            return invocation.proceed();
        } finally {
            request.removeHeaders(HttpHeaders.AUTHORIZATION);
        }
    }

}
