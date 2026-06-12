package com.plataforma.user.kyc.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataforma.user.kyc.service.KycService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Webhook de Didit — recibe el resultado de verificaciones KYC automatizadas.
 *
 * Este controller está en /internal/** para que el SecurityConfig lo permita
 * sin JWT (ver SecurityConfig: /internal/** → permitAll()).
 *
 * ¿Por qué /internal? El gateway expone /api/** al exterior pero NO /internal/**.
 * Didit llama directamente al user-service por ngrok (dev) o por la URL pública
 * configurada en Didit Dashboard → Webhooks, apuntando al servicio directamente.
 *
 * En producción (GKE): configurar el webhook de Didit apuntando al ingress del
 * user-service con una ruta específica, o usar un LoadBalancer dedicado.
 * La URL sería: https://[user-service-url]/internal/kyc/webhook/didit
 *
 * Payload esperado de Didit v3 (evento status.updated):
 * {
 *   "session_id": "...",
 *   "status": "APPROVED" | "DECLINED" | "IN_REVIEW",
 *   "vendor_data": "userId-nuestro",
 *   "decline_reasons": ["FAKE_DOCUMENT", ...]   // solo si DECLINED
 * }
 */
@Slf4j
@RestController
@RequestMapping("/internal/kyc")
@RequiredArgsConstructor
public class KycWebhookController {

    private final KycService kycService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Endpoint que Didit llama cuando termina una verificación.
     *
     * Siempre devuelve 200 para evitar reintentos innecesarios de Didit
     * (Didit reintenta con backoff exponencial si recibe != 2xx).
     *
     * En producción: verificar header X-Signature-V2 (HMAC-SHA256)
     * para confirmar que el request viene realmente de Didit.
     */
    @PostMapping("/webhook/didit")
    public ResponseEntity<Void> handleWebhook(@RequestBody String rawBody) {
        try {
            log.info("Webhook Didit recibido: {}", rawBody);
            JsonNode payload = objectMapper.readTree(rawBody);

            String sessionId    = payload.path("session_id").asText(null);
            String status       = payload.path("status").asText(null);
            String vendorData   = payload.path("vendor_data").asText(null);

            // Extraer primera razón de rechazo si existe
            String declineReason = null;
            JsonNode reasons = payload.path("decline_reasons");
            if (reasons.isArray() && !reasons.isEmpty()) {
                declineReason = reasons.get(0).asText();
            }

            if (sessionId == null || status == null) {
                log.warn("Webhook Didit con payload incompleto: {}", rawBody);
                return ResponseEntity.ok().build(); // 200 igual para no generar reintentos
            }

            kycService.processDiditWebhook(sessionId, status, vendorData, declineReason);

        } catch (Exception e) {
            // Loguear pero devolver 200 para evitar reintentos de Didit
            log.error("Error procesando webhook Didit: {}", e.getMessage(), e);
        }

        return ResponseEntity.ok().build();
    }
}
