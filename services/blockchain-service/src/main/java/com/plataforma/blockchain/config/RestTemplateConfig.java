package com.plataforma.blockchain.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agrega el header {@code X-Internal-Token} a cada request HTTP saliente.
 *
 * <p>Los endpoints {@code /internal/**} de user-service y project-service estan
 * protegidos por {@code InternalAuthFilter}, que rechaza con 403 cualquier
 * request sin este header. Este customizer aplica el header a todos los
 * {@code RestTemplate} construidos via {@code RestTemplateBuilder} en el
 * blockchain-service (UserLookupClient, ProjectServiceClient, etc.).
 *
 * <p>El valor se toma de la env var {@code INTERNAL_API_KEY} (default vacio
 * para tests). En produccion debe coincidir con el mismo secreto que tienen
 * user-service y project-service.
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
