package ch.fhnw.digibp.classroom.modeler;

import ch.fhnw.digibp.classroom.service.AiModelService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.cibseven.webapp.agent.BpmnAgentProperties;
import org.cibseven.webapp.agent.BpmnAgentProperties.Model;
import org.cibseven.webapp.ai.bpmn.agent.chat.ChatModelFactory;
import org.cibseven.webapp.ai.bpmn.agent.openai.OpenAiChatModelFactory;
import org.cibseven.webapp.ai.bpmn.agent.openai.OpenAiChatModelFactory.ProviderConfig;
import org.cibseven.webapp.ai.bpmn.agent.orchestration.BpmnAgentOrchestrator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "cibseven.webclient.modeler", name = {"enabled", "ai.enabled"}, havingValue = "true")
public final class DynamicModelRegistry implements AiModelService.Modeler {

    private final BpmnAgentProperties properties;
    private final String providerName;
    private volatile Routing routing;

    public DynamicModelRegistry(BpmnAgentProperties properties) {
        this.properties = properties;
        providerName = properties.getModels().stream()
                .filter(model -> model.getValue().equals(properties.getDefaultModel()))
                .findFirst().orElseThrow().getProvider();
        Map<String, ProviderConfig> initialRouting = new LinkedHashMap<>();
        for (Model model : properties.getModels()) {
            if (model.getLabel() == null || model.getLabel().isBlank()) {
                model.setLabel(model.getValue());
            }
            initialRouting.put(model.getValue(), providerConfig(model.getProvider()));
        }
        routing = new Routing(properties.getDefaultModel(), Map.copyOf(initialRouting));
    }

    @Bean
    public static BeanPostProcessor dynamicModelIntegration(ObjectProvider<DynamicModelRegistry> registry) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof BpmnAgentOrchestrator orchestrator) {
                    registry.getObject().install(orchestrator);
                }
                return bean;
            }
        };
    }

    private void install(BpmnAgentOrchestrator orchestrator) {
        // Replace only model routing, retaining the orchestrator and all existing chat sessions.
        orchestrator.setChatModelFactory(new ChatModelFactory() {
            private OpenAiChatModelFactory currentFactory() {
                Routing current = routing;
                return new OpenAiChatModelFactory(current.providers(), current.defaultModel(),
                        orchestrator.getActivityTracker());
            }

            @Override
            public ChatModel create(String model, String session, String role, String effort) {
                return currentFactory().create(model, session, role, effort);
            }

            @Override
            public StreamingChatModel createStreaming(String model, String session, String role, String effort) {
                return currentFactory().createStreaming(model, session, role, effort);
            }
        });
    }

    @Override
    public synchronized void configure(String defaultModel, List<String> values) {
        ProviderConfig config = providerConfig(providerName);
        // Retain routes for chats already in progress; the selectable list is replaced below.
        Map<String, ProviderConfig> updatedRouting = new LinkedHashMap<>(routing.providers());
        values.forEach(value -> updatedRouting.put(value, config));
        List<Model> models = values.stream().map(value -> {
            Model model = new Model();
            model.setValue(value);
            model.setLabel(value);
            model.setProvider(providerName);
            return model;
        }).toList();
        routing = new Routing(defaultModel, Map.copyOf(updatedRouting));
        // CIB reads these properties for the dropdown, default and chat model validation.
        properties.setModels(models);
        properties.setDefaultModel(defaultModel);
    }

    private ProviderConfig providerConfig(String name) {
        var provider = properties.getProviders().get(name);
        return new ProviderConfig(provider.getBaseUrl(), provider.getApiKey(),
                provider.getTimeoutSeconds(), provider.getApi(), provider.getHeaders());
    }

    private record Routing(String defaultModel, Map<String, ProviderConfig> providers) {
    }
}
