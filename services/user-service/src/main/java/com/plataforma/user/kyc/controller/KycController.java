package com.plataforma.user.kyc.controller;

import com.plataforma.shared.dto.ApiResponse;
import com.plataforma.user.kyc.dto.KycReviewRequest;
import com.plataforma.user.kyc.dto.KycStatusResponse;
import com.plataforma.user.kyc.service.KycService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

/**
 * Endpoints publicos de KYC (ver DD013).
 *
 *   POST /api/users/me/kyc           — usuario sube documentos (multipart)
 *   GET  /api/users/me/kyc           — usuario consulta su estado
 *   PUT  /api/users/{id}/kyc         — ADMIN aprueba o rechaza
 */

@Slf4j   
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class KycController {

    private final KycService kycService;

    @PostMapping(value = "/me/kyc", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<KycStatusResponse>> uploadDocuments(
            Authentication auth,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("types") List<String> types) {

        Long userId = (Long) auth.getPrincipal();
        KycStatusResponse response = kycService.uploadDocuments(userId, files, types);
        return ResponseEntity.ok(ApiResponse.success("Documentos KYC subidos", response));
    }

    @GetMapping("/me/kyc")
    public ResponseEntity<ApiResponse<KycStatusResponse>> getMyKycStatus(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        KycStatusResponse response = kycService.getMyKycStatus(userId);
        return ResponseEntity.ok(ApiResponse.success("OK", response));
    }

    @PutMapping("/{id}/kyc")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('kyc:review')")
    public ResponseEntity<ApiResponse<KycStatusResponse>> review(
            @PathVariable("id") Long targetUserId,
            @RequestBody KycReviewRequest request,
            Authentication auth) {

        Long reviewerId = (Long) auth.getPrincipal();
        KycStatusResponse response = kycService.review(
                targetUserId,
                request.getDecision(),
                request.getRejectionReason(),
                reviewerId);
        return ResponseEntity.ok(ApiResponse.success(
                "KYC del usuario " + targetUserId + " → " + request.getDecision(),
                response));
    }

    /**
     * Inicia una sesión de verificación KYC con Didit para el usuario autenticado.
     *
     * POST /api/users/me/kyc/didit/initiate
     *
     * El frontend redirige al usuario a la URL devuelta.
     * Didit maneja todo: captura del DNI, liveness check, biometría.
     * El resultado llega por webhook a /api/kyc/webhook/didit.
     */
    @PostMapping("/me/kyc/didit/initiate")
    public ResponseEntity<ApiResponse<Map<String, String>>> initiateDiditKyc(
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        try {
            String verificationUrl = kycService.initiateDiditSession(userId);
            return ResponseEntity.ok(ApiResponse.success(
                "Sesión KYC iniciada",
                Map.of("verificationUrl", verificationUrl)
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage(), 400));
        } catch (Exception e) {
            log.error("Error iniciando sesión Didit para userId {}: {}", userId, e.getMessage());
            return ResponseEntity.internalServerError()
                 .body(ApiResponse.error("No se pudo iniciar la verificación: " + e.getMessage(), 500));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // WEBHOOK — va en un controller SEPARADO (KycWebhookController.java)
    // porque necesita estar fuera del JWT filter.


}
