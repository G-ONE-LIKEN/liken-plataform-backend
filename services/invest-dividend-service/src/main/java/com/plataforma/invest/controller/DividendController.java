// services/invest-dividend-service/src/main/java/com/plataforma/invest/controller/DividendController.java
package com.plataforma.invest.controller;

import com.plataforma.invest.dto.DividendClaimResponse;
import com.plataforma.invest.dto.PendingDividendsResponse;
import com.plataforma.invest.service.ChainReader;
import com.plataforma.invest.service.DividendService;
import com.plataforma.invest.service.UserWalletReconciliationService;
import com.plataforma.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/dividends")
@RequiredArgsConstructor
public class DividendController {

    private final DividendService dividendService;
    private final ChainReader chainReader;
    private final UserWalletReconciliationService userWalletReconciliationService;

    /**
     * Historial de dividendos reclamados por el usuario autenticado.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Page<DividendClaimResponse>>> myClaims(
            Authentication auth,
            Pageable pageable) {
        Long userId = (Long) auth.getPrincipal();
        userWalletReconciliationService.reconcileIfNeeded(userId);
        Page<DividendClaimResponse> page = dividendService.listForUser(userId, pageable)
                .map(DividendClaimResponse::from);
        return ResponseEntity.ok(ApiResponse.success("OK", page));
    }

    /**
     * Lee on-chain los dividendos pendientes de una wallet
     * ({@code DividendDistributor.pendingDividends}). El frontend lo usa para
     * mostrar "USD X.XX disponibles para reclamar" en el wallet card.
     *
     * <p>El parametro es la wallet on-chain (no el userId): asi el frontend
     * puede pedir info de cualquier wallet que conozca, sin necesidad de
     * vincularla a un usuario.
     */
    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<PendingDividendsResponse>> pending(
            @RequestParam String wallet) {
        BigDecimal pending = chainReader.pendingDividends(wallet);
        return ResponseEntity.ok(ApiResponse.success("OK", PendingDividendsResponse.builder()
                .walletAddress(wallet)
                .pendingUsdc(pending)
                .build()));
    }
}
