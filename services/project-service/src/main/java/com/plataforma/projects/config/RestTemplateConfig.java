package com.plataforma.projects.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RestTemplateConfig {

    @Value("${INTERNAL_API_KEY:}")
    private String internalApiKey;

    @Bean
    public RestTemplateCustomizer internalTokenCustomizer() {
        return restTemplate -> restTemplate.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().add("X-Internal-Token", internalApiKey);
            return execution.execute(request, body);
        });
    }
}
