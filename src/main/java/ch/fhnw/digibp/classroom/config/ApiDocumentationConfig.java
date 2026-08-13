/*
 * Copyright (c) 2021. University of Applied Sciences and Arts Northwestern Switzerland FHNW.
 * All rights reserved.
 */

package ch.fhnw.digibp.classroom.config;

import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiDocumentationConfig {

    @Bean
    public GroupedOpenApi messageApi() {
        return GroupedOpenApi.builder()
                .group("message-api")
                .displayName("DigiBP Message API")
                .pathsToMatch("/message/**")
                .addOpenApiCustomizer(openApi -> openApi.info(apiInfo("DigiBP Message API")))
                .build();
    }

    @Bean
    public GroupedOpenApi classroomApi() {
        return GroupedOpenApi.builder()
                .group("classroom-api")
                .displayName("DigiBP Classroom API")
                .pathsToMatch("/classroom/**")
                .addOpenApiCustomizer(openApi -> openApi.info(apiInfo("DigiBP Classroom API")))
                .build();
    }

    private Info apiInfo(String title) {
        return new Info().title(title).version("1.0.0");
    }
}
