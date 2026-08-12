/*
 * Copyright (c) 2019. University of Applied Sciences and Arts Northwestern Switzerland FHNW.
 * All rights reserved.
 */

package ch.fhnw.digibp.classroom.config;

import org.cibseven.bpm.engine.rest.security.auth.ProcessEngineAuthenticationFilter;
import org.cibseven.bpm.engine.IdentityService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import jakarta.servlet.Filter;

@Configuration
public class CamundaSecurityFilter {

    private final IdentityService identityService;

    public CamundaSecurityFilter(IdentityService identityService) {
        this.identityService = identityService;
    }

    @Value("${cibseven.webclient.authentication.jwtSecret}")
    private String jwtSecret;

    @Bean
    public FilterRegistrationBean processEngineAuthenticationFilter() {
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
        return new ProcessEngineAuthenticationFilter();
    }

    @Bean
    public Filter httpsEnforcerFilter(){
        return new HttpsFilter();
    }
}
