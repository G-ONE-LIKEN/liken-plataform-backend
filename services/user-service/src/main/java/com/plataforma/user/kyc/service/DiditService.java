package com.plataforma.user.kyc.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Slf4j
@Service
public class DiditService {

    private static final String API_BASE = "https://verification.didit.me";

    @Value("${didit.api-key:}")
    private String apiKey;

    @Value("${didit.workflow-id:}")
    private String workflowId;

    @Value("${didit.callback-url:}")
    private String callbackUrl;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @PostConstruct
    void validateConfig() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("DIDIT_API_KEY no configurada — la verificación KYC con Didit no funcionará");
        }
        if (workflowId == null || workflowId.isBlank()) {
            log.warn("DIDIT_WORKFLOW_ID no configurado — la verificación KYC con Didit no funcionará");
        }
    }

    /**
     * Crea una sesión de verificación KYC en Didit.
     *
     * POST /v3/session/
     *
     * @param externalUserId  ID del usuario en nuestra DB (se incluye en vendor_data
     *                        y Didit lo devuelve en el webhook para que podamos
     *                        identificar al usuario sin depender solo del session_id)
     * @return {@link DiditSession} con el sessionId y la URL del widget
     */
    public DiditSession createSession(String externalUserId, ExpectedDetails expectedDetails) throws Exception {
        if (apiKey == null || apiKey.isBlank() || workflowId == null || workflowId.isBlank()) {
            throw new IllegalStateException(
                "Didit no está configurado. Verificar variables DIDIT_API_KEY y DIDIT_WORKFLOW_ID");
        }

        log.info("Creando sesión Didit para userId: {}", externalUserId);

        ObjectNode body = mapper.createObjectNode();
        body.put("workflow_id", workflowId.trim());
        body.put("vendor_data", externalUserId);

        // callback: URL a la que Didit redirige al usuario al terminar.
        // Solo se incluye si está configurado — campo vacío rompe la API de Didit.
        if (callbackUrl != null && !callbackUrl.isBlank()) {
            body.put("callback", callbackUrl);
        }

        if (expectedDetails != null) {
            ObjectNode details = mapper.createObjectNode();
            if (expectedDetails.firstName() != null)
                details.put("first_name", expectedDetails.firstName());
            if (expectedDetails.lastName() != null)
                details.put("last_name", expectedDetails.lastName());
            if (expectedDetails.documentNumber() != null)
                details.put("document_number", expectedDetails.documentNumber());
            if (expectedDetails.dateOfBirth() != null)
                details.put("date_of_birth", expectedDetails.dateOfBirth());
            body.set("expected_details", details);
        }

        String requestBody = mapper.writeValueAsString(body);
        log.info("Didit POST /v3/session/ request body: {}", requestBody);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_BASE + "/v3/session/"))
            .header("x-api-key", apiKey.trim())
            .header("Content-Type", "application/json")
            .header("accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        log.info("Didit POST /v3/session/ → {}: {}", response.statusCode(), response.body());

        if (response.statusCode() != 200 && response.statusCode() != 201) {
            throw new RuntimeException("Didit API error " + response.statusCode()
                + ": " + response.body());
        }

        JsonNode json = mapper.readTree(response.body());
        String sessionId      = json.path("session_id").asText();
        String verificationUrl = json.path("url").asText();

        if (sessionId.isBlank() || verificationUrl.isBlank()) {
            throw new RuntimeException("Respuesta de Didit incompleta: " + response.body());
        }

        log.info("Sesión Didit creada: {} para userId: {}", sessionId, externalUserId);
        return new DiditSession(sessionId, verificationUrl);
    }

    public record ExpectedDetails(
        String firstName,
        String lastName,
        String documentNumber,
        String dateOfBirth
    ) {}

    /**
     * Consulta el estado actual de una sesión en Didit.
     * Útil como fallback si el webhook no llegó.
     *
     * GET /v3/session/{sessionId}/
     */
    public String getSessionStatus(String sessionId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_BASE + "/v3/session/" + sessionId + "/"))
            .header("x-api-key", apiKey.trim())
            .header("accept", "application/json")
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Error consultando sesión Didit "
                + sessionId + ": " + response.statusCode());
        }

        JsonNode json = mapper.readTree(response.body());
        return json.path("status").asText("UNKNOWN");
    }

    /** DTO interno: resultado de crear una sesión en Didit. */
    public record DiditSession(String sessionId, String verificationUrl) {}
}
