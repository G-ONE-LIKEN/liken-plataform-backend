package com.plataforma.invest.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

/**
 * Cliente que consulta {@code project-service} para obtener el precio vigente
 * y el estado de un proyecto (necesario para el preview de compra).
 *
 * <p>Pega al endpoint público {@code GET /api/projects/{id}} y extrae
 * {@code currentPrice} + {@code state}.
 */
@Slf4j
@Service
public class ProjectClient {

    private final RestTemplate restTemplate;
    private final String projectServiceUrl;

    public ProjectClient(RestTemplateBuilder builder,
                         @Value("${services.project-service-url}") String projectServiceUrl) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
        this.projectServiceUrl = projectServiceUrl;
    }

    /**
     * @return precio vigente del LKN en USD (escala 4 — tal cual lo devuelve el backend),
     *         o {@code null} si el proyecto no existe / falla la llamada.
     */
    @SuppressWarnings("unchecked")
    public ProjectSnapshot fetch(Long projectId) {
        try {
            Map<String, Object> body = restTemplate.getForObject(
                    projectServiceUrl + "/api/projects/{id}",
                    Map.class,
                    projectId);
            if (body == null) return null;
            // El project-service envuelve en ApiResponse<{data: ProjectResponse}>.
            Map<String, Object> data = (Map<String, Object>) body.getOrDefault("data", body);
            BigDecimal currentPrice = toBigDecimal(data.get("currentPrice"));
            String state = String.valueOf(data.get("state"));
            String offering = (String) data.get("offeringContractAddress");
            return new ProjectSnapshot(currentPrice, state, offering);
        } catch (Exception ex) {
            log.warn("project-service /api/projects/{} falló: {}", projectId, ex.getMessage());
            return null;
        }
    }

    private static BigDecimal toBigDecimal(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return new BigDecimal(n.toString());
        return new BigDecimal(v.toString());
    }

    public record ProjectSnapshot(BigDecimal currentPrice, String state, String offeringContractAddress) {
    }
}
