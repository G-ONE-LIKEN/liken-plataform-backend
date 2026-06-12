package com.plataforma.gateway.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.plataforma.gateway.model.UserContext;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class UserContextService {

    private final WebClient.Builder webClientBuilder;
    private final ReactiveCircuitBreakerFactory<?, ?> circuitBreakerFactory;

    @Value("${app.services.usuarios-url}")
    private String userServiceUrl;

    @Value("${app.user-context.ttl-seconds:30}")
    private int ttlSeconds;

    private Cache<Long, UserContext> cache;
    private ReactiveCircuitBreaker circuitBreaker;

    @PostConstruct
    void init() {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                .maximumSize(10_000)
                .build();
        // Config en ResilienceConfig: timeout 3s + breaker que ignora 404.
        this.circuitBreaker = circuitBreakerFactory.create("user-context");
    }

    public Mono<UserContext> getContext(Long userId) {
        UserContext cached = cache.getIfPresent(userId);
        if (cached != null) return Mono.just(cached);

        Mono<UserContext> remote = webClientBuilder.build()
                .get()
                .uri(userServiceUrl + "/internal/users/{id}/context", userId)
                .retrieve()
                .bodyToMono(UserContext.class)
                .doOnNext(ctx -> cache.put(userId, ctx));

        // Sin fallback: el error llega a JwtAuthFilter, que distingue
        // 404 (usuario inválido → 401) de falla del servicio (→ 503).
        return circuitBreaker.run(remote);
    }

    public void invalidate(Long userId) {
        cache.invalidate(userId);
    }
}
