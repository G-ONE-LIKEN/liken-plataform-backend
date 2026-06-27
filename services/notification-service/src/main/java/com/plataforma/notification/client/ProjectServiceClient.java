package com.plataforma.notification.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Cliente HTTP simple hacia project-service para mapear direcciones de contratos
 * de proyectos a sus correspondientes identificadores (projectId).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectServiceClient {

    @Value("${project-service.base-url}")
    private String baseUrl;

    @Value("${INTERNAL_API_KEY:}")
    private String internalApiKey;

    private RestClient restClient;

    private RestClient client() {
        if (restClient == null) {
            restClient = RestClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader("X-Internal-Token", internalApiKey)
                    .build();
        }
        return restClient;
    }

    @SuppressWarnings("unchecked")
    public Long resolveProjectIdByOffering(String offeringContractAddress) {
        if (offeringContractAddress == null || offeringContractAddress.isBlank()) {
            return null;
        }
        try {
            List<Map<String, Object>> body = client().get()
                    .uri("/internal/projects/offering-contracts")
                    .retrieve()
                    .body(List.class);
            if (body == null) return null;
            return body.stream()
                    .filter(ref -> offeringContractAddress.equalsIgnoreCase(String.valueOf(ref.get("offeringContractAddress"))))
                    .map(ref -> {
                        Object val = ref.get("projectId");
                        if (val instanceof Number n) return n.longValue();
                        return Long.parseLong(val.toString());
                    })
                    .findFirst()
                    .orElse(null);
        } catch (Exception ex) {
            log.warn("No se pudo mapear offeringContractAddress {} a projectId: {}", offeringContractAddress, ex.getMessage());
            return null;
        }
    }
}
