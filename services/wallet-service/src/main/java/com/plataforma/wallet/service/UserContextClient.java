package com.plataforma.wallet.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Slf4j
@Service
public class UserContextClient {

    private final RestTemplate restTemplate;
    private final String userServiceUrl;

    public UserContextClient(RestTemplateBuilder builder,
                             @Value("${services.user-service-url:http://user-service:8080}") String userServiceUrl) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
        this.userServiceUrl = userServiceUrl;
    }

    public UserContext fetch(Long userId) {
        try {
            return restTemplate.getForObject(
                    userServiceUrl + "/internal/users/{id}/context",
                    UserContext.class,
                    userId);
        } catch (Exception ex) {
            log.warn("user-service /internal/users/{}/context fallo: {}", userId, ex.getMessage());
            return null;
        }
    }

    public record UserContext(String walletAddress) {
    }
}
