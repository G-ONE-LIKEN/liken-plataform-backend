package com.plataforma.user.kyc.controller;

import com.plataforma.user.kyc.dto.KycInternalDto;
import com.plataforma.user.kyc.service.KycService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints internos para KYC (sin JWT — ver DD003 y DD013).
 *
 * Consumidos por:
 *   - invest-dividend-service.POST /api/investments → check KYC antes de aceptar
 *   - marketplace-service.POST /api/orders          → check KYC antes de aceptar
 *
 * No están expuestos por el gateway al exterior. Protegidos por ClusterIP en Kubernetes.
 */
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class KycInternalController {

    private final KycService kycService;

    @GetMapping("/{id}/kyc-status")
    public ResponseEntity<KycInternalDto> getKycStatus(@PathVariable("id") Long userId) {
        return ResponseEntity.ok(kycService.getInternalStatus(userId));
    }
}
