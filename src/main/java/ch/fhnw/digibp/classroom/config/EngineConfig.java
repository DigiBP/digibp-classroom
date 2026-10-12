/*
 * Copyright (c) 2019. University of Applied Sciences and Arts Northwestern Switzerland FHNW.
 * All rights reserved.
 */

package ch.fhnw.digibp.classroom.config;

import ch.fhnw.digibp.classroom.tenant.TenantIdProviderPlugin;
import onl.mrtn.camunda.http.plugin.NotifyProcessEnginePlugin;
import org.cibseven.bpm.engine.impl.cfg.AbstractProcessEnginePlugin;
import org.cibseven.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.cibseven.bpm.engine.impl.cfg.ProcessEnginePlugin;
import org.cibseven.bpm.engine.spring.SpringExpressionManager;
import org.cibseven.bpm.spring.boot.starter.configuration.Ordering;
import org.cibseven.connect.Connectors;
import org.cibseven.connect.httpclient.HttpConnector;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
public class EngineConfig {

    @Bean
    public static ProcessEnginePlugin httpAuthenticationConfiguration(Environment environment) {
        HttpAuthenticationInterceptor interceptor = new HttpAuthenticationInterceptor(environment);
        return new AbstractProcessEnginePlugin() {
            @Override
            public void postInit(ProcessEngineConfigurationImpl configuration) {
                // Connect's preInit loads the shared connectors before this hook runs.
                HttpConnector http = Connectors.http();
                http.addRequestInterceptor(interceptor);
            }
        };
    }

    @Bean
    public static ProcessEnginePlugin scriptAccessConfiguration() {
        return new ScriptAccessPlugin();
    }

    @Bean
    @Order(Ordering.DEFAULT_ORDER + 1)
    public static ProcessEnginePlugin expressionBeanConfiguration(ApplicationContext applicationContext) {
        // Expose process-facing beans only, never Spring infrastructure or configuration.
        Set<Object> names = Set.of("api_connect", "notify", "inter_process", "logger",
                "messageBroker", "classroomHttpConnectService");
        Map<Object, Object> beans = new AbstractMap<>() {
            @Override
            public boolean containsKey(Object key) {
                return names.contains(key);
            }

            @Override
            public Object get(Object key) {
                // Resolve on use: delegates depend on engine services during startup.
                return containsKey(key) ? applicationContext.getBean((String) key) : null;
            }

            @Override
            public Set<Object> keySet() {
                return names;
            }

            @Override
            public Set<Entry<Object, Object>> entrySet() {
                return names.stream()
                        .map(name -> new SimpleImmutableEntry<>(name, get(name)))
                        .collect(Collectors.toUnmodifiableSet());
            }
        };
        return new AbstractProcessEnginePlugin() {
            @Override
            public void preInit(ProcessEngineConfigurationImpl configuration) {
                // The factory already created its expression manager before preInit.
                // SpringExpressionManager also avoids the process-application EL fallback.
                configuration.setBeans(beans);
                configuration.setExpressionManager(new SpringExpressionManager(null, beans));
            }
        };
    }

    @Bean
    @Order(Ordering.DEFAULT_ORDER + 1)
    public static ProcessEnginePlugin tenantIdConfiguration() {
        return new TenantIdProviderPlugin();
    }

    @Bean
    public static NotifyProcessEnginePlugin httpNotifyProcessEnginePluginConfiguration() {
        return new NotifyProcessEnginePlugin();
    }
}
