/*
 * Copyright (c) 2019. University of Applied Sciences and Arts Northwestern Switzerland FHNW.
 * All rights reserved.
 */

package ch.fhnw.digibp.classroom.config;

import jakarta.servlet.Filter;
import org.cibseven.bpm.engine.IdentityService;
import org.cibseven.bpm.engine.ProcessEngine;
import org.cibseven.bpm.engine.rest.security.auth.ProcessEngineAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.util.List;

@Configuration
public class SecurityConfig {

    private final IdentityService identityService;

    public SecurityConfig(IdentityService identityService) {
        this.identityService = identityService;
    }

    @Value("${cibseven.webclient.authentication.jwtSecret}")
    private String jwtSecret;

    @Bean
    public FilterRegistrationBean<Filter> processEngineAuthenticationFilter() {
        System.setProperty("cibseven.webclient.authentication.jwtSecret", jwtSecret);
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setName("cibseven-auth");
        registration.setFilter(getProcessEngineAuthenticationFilter());
        registration.addInitParameter("authentication-provider",
                "org.cibseven.bpm.engine.rest.security.auth.impl.CompositeAuthenticationProvider");
        registration.addUrlPatterns("/engine-rest/*", "/classroom/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<TenantAdminSecurityFilter> tenantAdminSecurityFilter() {
        FilterRegistrationBean<TenantAdminSecurityFilter> registration = new FilterRegistrationBean<>();
        registration.setName("tenant-admin-security");
        registration.setFilter(new TenantAdminSecurityFilter(identityService));
        registration.addUrlPatterns("/engine-rest/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 11);
        return registration;
    }

    @Bean
    public Filter getProcessEngineAuthenticationFilter() {
        return new ProcessEngineAuthenticationFilter() {
            @Override
            protected List<String> getTenantsOfUser(ProcessEngine engine, String userId) {
                // Role groups are shared across all classroom tenants. Resolving tenant
                // access through groups would therefore expose every tenant to a user.
                return engine.getIdentityService().createTenantQuery()
                        .userMember(userId)
                        .list().stream()
                        .map(tenant -> tenant.getId())
                        .toList();
            }
        };
    }

    @Bean
    public Filter httpsEnforcerFilter() {
        return new HttpsFilter();
    }
}
