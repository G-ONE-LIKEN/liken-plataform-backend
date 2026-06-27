package com.plataforma.invest.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Cliente HTTP a project-service para obtener los holders de un proyecto.
 * Endpoint: {@code GET /internal/projects/{id}/holders}.
 *
 * <p>Reusa el {@link org.springframework.boot.web.client.RestTemplateBuilder}
 * de Spring Boot, que aplica automaticamente el customizer de
 * {@code RestTemplateConfig} que inyecta {@code X-Internal-Token}.
 */
@Slf4j
@Service
public class HoldingsClient {

    private final RestTemplate restTemplate;
    private final String projectServiceUrl;

    public HoldingsClient(RestTemplateBuilder builder,
                          @Value("${services.project-service-url}") String projectServiceUrl) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
        this.projectServiceUrl = projectServiceUrl;
    }

    public List<Holder> fetchHolders(Long projectId) {
        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    projectServiceUrl + "/internal/projects/" + projectId + "/holders",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {});
            List<Map<String, Object>> body = response.getBody();
            if (body == null) return List.of();
            return body.stream()
                    .map(m -> new Holder(
                            toLong(m.get("userId")),
                            str(m.get("walletAddress")),
                            bigDecimal(m.get("tokensAmount"))))
                    .toList();
        } catch (Exception ex) {
            log.warn("GET /internal/projects/{}/holders fallo: {}", projectId, ex.getMessage());
            return List.of();
        }
    }

    public record Holder(Long userId, String walletAddress, BigDecimal tokensAmount) {}

    private static String str(Object v) {
        return v == null ? null : v.toString();
    }

    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        String s = v.toString();
        return s.isBlank() ? null : Long.parseLong(s);
    }

    private static BigDecimal bigDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof Number n) return new BigDecimal(n.toString());
        return new BigDecimal(v.toString());
    }
}
