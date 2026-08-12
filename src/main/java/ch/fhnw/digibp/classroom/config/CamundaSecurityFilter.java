/*
 * Copyright (c) 2019. University of Applied Sciences and Arts Northwestern Switzerland FHNW.
 * All rights reserved.
 */

package ch.fhnw.digibp.classroom.config;

import org.cibseven.bpm.engine.rest.security.auth.ProcessEngineAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.servlet.Filter;

@Configuration
public class CamundaSecurityFilter {

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
