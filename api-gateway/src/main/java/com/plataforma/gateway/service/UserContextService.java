package com.plataforma.gateway.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.plataforma.gateway.model.UserContext;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class UserContextService {

    private final WebClient.Builder webClientBuilder;

    @Value("${app.services.usuarios-url}")
    private String userServiceUrl;

    @Value("${app.user-context.ttl-seconds:30}")
    private int ttlSeconds;

    @Value("${INTERNAL_API_KEY:}")
    private String internalApiKey;

    private Cache<Long, UserContext> cache;

    @PostConstruct
    void init() {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                .maximumSize(10_000)
                .build();
    }

    public Mono<UserContext> getContext(Long userId) {
        UserContext cached = cache.getIfPresent(userId);
        if (cached != null) return Mono.just(cached);

        return webClientBuilder.build()
                .get()
                .uri(userServiceUrl + "/internal/users/{id}/context", userId)
                .header("X-Internal-Token", internalApiKey)
                .retrieve()
                .bodyToMono(UserContext.class)
                .doOnNext(ctx -> cache.put(userId, ctx));
    }

    public void invalidate(Long userId) {
        cache.invalidate(userId);
    }
}
