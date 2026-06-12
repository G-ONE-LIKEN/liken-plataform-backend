package com.plataforma.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;

/**
 * Configuración del circuit breaker "user-context" que protege la llamada
 * del gateway a user-service en cada request autenticado (JwtAuthFilter).
 *
 * - TimeLimiter de 3s: ningún request queda colgado esperando el contexto.
 * - Ventana de 10 llamadas, abre al 50% de fallas, half-open a los 15s.
 * - Un 404 (usuario inexistente/desactivado) es una respuesta de negocio,
 *   no una falla del servicio: no cuenta para abrir el breaker.
 */
@Configuration
public class ResilienceConfig {

    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> userContextCircuitBreaker() {
        return factory -> factory.configure(builder -> builder
                        .circuitBreakerConfig(CircuitBreakerConfig.custom()
                                .slidingWindowSize(10)
                                .minimumNumberOfCalls(5)
                                .failureRateThreshold(50)
                                .waitDurationInOpenState(Duration.ofSeconds(15))
                                .permittedNumberOfCallsInHalfOpenState(2)
                                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                                .ignoreExceptions(WebClientResponseException.NotFound.class)
                                .build())
                        .timeLimiterConfig(TimeLimiterConfig.custom()
                                .timeoutDuration(Duration.ofSeconds(3))
                                .build()),
                "user-context");
    }
}
