package com.plataforma.marketplace.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Cliente HTTP contra project-service para validar estado de proyectos
 * y holdings del vendedor antes de crear ordenes o ejecutar matches.
 *
 * <p>Consulta endpoints internos (red privada, sin JWT).
 */
@Slf4j
@Component
public class ProjectClient {

    private final RestTemplate restTemplate;
    private final String projectServiceUrl;

    public ProjectClient(
            @Value("${services.project-service-url:http://project-service:8082}") String projectServiceUrl) {
        this.restTemplate = new RestTemplate();
        this.projectServiceUrl = projectServiceUrl;
    }

    /**
     * Verifica si el proyecto existe y su ronda primaria finalizo exitosamente
     * (solo entonces los tokens son "activos" y se pueden negociar en el mercado).
     *
     * @return true si el proyecto existe y el trading es valido.
     */
    public boolean isProjectTradeable(Long projectId) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(
                    projectServiceUrl + "/internal/projects/{id}",
                    Map.class, projectId);

            if (response == null) return false;

            // El proyecto tiene que estar en estado CLOSED (ronda exitosa) o OPEN
            // para permitir trading en el marketplace.
            String state = String.valueOf(response.getOrDefault("state", ""));
            String roundState = String.valueOf(response.getOrDefault("roundState", ""));

            // Trading permitido: ronda finalizada exitosamente.
            boolean tradeable = "OPEN".equalsIgnoreCase(state) ||
                    "CLOSED".equalsIgnoreCase(state) ||
                    "FINALIZED".equalsIgnoreCase(roundState);

            log.debug("Proyecto {} tradeable={} (state={}, roundState={})",
                    projectId, tradeable, state, roundState);
            return tradeable;
        } catch (RestClientException e) {
            log.warn("Error consultando project-service para projectId={}: {}",
                    projectId, e.getMessage());
            return false;
        }
    }

    /**
     * Consulta los holdings de un usuario en un proyecto especifico.
     *
     * @return cantidad de tokens que el usuario posee (0 si no tiene holdings o falla).
     */
    public BigDecimal getUserHoldings(Long userId, Long projectId) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(
                    projectServiceUrl + "/internal/projects/{projectId}/holders/{userId}",
                    Map.class, projectId, userId);

            if (response == null) return BigDecimal.ZERO;

            Object tokensAmount = response.get("tokensAmount");
            if (tokensAmount == null) return BigDecimal.ZERO;

            return new BigDecimal(tokensAmount.toString());
        } catch (RestClientException e) {
            log.warn("Error consultando holdings: userId={} projectId={}: {}",
                    userId, projectId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }
}
