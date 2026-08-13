/*
 * Copyright (c) 2021. University of Applied Sciences and Arts Northwestern Switzerland FHNW.
 * All rights reserved.
 */

package ch.fhnw.digibp.classroom.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@RestController
public class ApiDocsController {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @GetMapping(value = "/cibseven-rest/openapi.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getCibSevenApiDocs() throws Exception {
        String[] candidates = new String[] {
                "classpath*:META-INF/resources/openapi.json",
                "classpath*:openapi.json",
                "classpath*:**/openapi.json"
        };

        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        for (String pattern : candidates) {
            Resource[] resources = resolver.getResources(pattern);
            for (Resource resource : resources) {
                if (!resource.exists() || !resource.isReadable()) {
                    continue;
                }

                String resourcePath = resource.getURL().toString().toLowerCase(Locale.ROOT);
                if (!resourcePath.contains("cibseven") && !resourcePath.contains("engine-rest-openapi")) {
                    continue;
                }

                try (InputStream in = resource.getInputStream()) {
                    String openapiJson = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    String modifiedJson = useEmbeddedEngineServer(openapiJson);
                    return ResponseEntity.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(modifiedJson);
                }
            }
        }

        return ResponseEntity.notFound().build();
    }

    private String useEmbeddedEngineServer(String openapiJson) throws Exception {
        JsonNode root = OBJECT_MAPPER.readTree(openapiJson);
        ArrayNode servers = OBJECT_MAPPER.createArrayNode();
        ObjectNode server = servers.addObject();
        server.put("url", "/engine-rest");
        server.put("description", "Embedded CIB seven process engine");
        ((ObjectNode) root).set("servers", servers);
        return OBJECT_MAPPER.writeValueAsString(root);
    }
}
