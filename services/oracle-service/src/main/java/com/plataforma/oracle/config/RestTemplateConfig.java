package com.plataforma.oracle.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Inyecta el header {@code X-Internal-Token} en todas las llamadas salientes
 * del oracle a otros servicios. Sin esto, los endpoints {@code /internal/**}
 * (validados por {@code InternalAuthFilter}) responden 403 — que es por lo
 * que el oracle no podia listar proyectos activos y nunca emitia lecturas.
 */
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
