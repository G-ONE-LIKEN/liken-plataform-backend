package com.plataforma.user.kyc.controller;

import com.plataforma.shared.dto.ApiResponse;
import com.plataforma.user.kyc.dto.KycReviewRequest;
import com.plataforma.user.kyc.dto.KycStatusResponse;
import com.plataforma.user.kyc.service.KycService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Endpoints públicos de KYC (ver DD013).
 *
 *   POST /api/users/me/kyc           — usuario sube documentos (multipart)
 *   GET  /api/users/me/kyc           — usuario consulta su estado
 *   PUT  /api/users/{id}/kyc         — ADMIN aprueba o rechaza
 */
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
}
