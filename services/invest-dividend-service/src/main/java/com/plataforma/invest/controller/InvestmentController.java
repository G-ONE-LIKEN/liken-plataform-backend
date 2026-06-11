// services/invest-dividend-service/src/main/java/com/plataforma/invest/controller/InvestmentController.java
package com.plataforma.invest.controller;

import com.plataforma.invest.dto.InvestmentResponse;
import com.plataforma.invest.dto.InvestmentTotalResponse;
import com.plataforma.invest.dto.PreviewResponse;
import com.plataforma.invest.service.InvestmentService;
import com.plataforma.invest.service.ProjectClient;
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
import java.math.RoundingMode;

@RestController
@RequestMapping("/api/investments")
@RequiredArgsConstructor
public class InvestmentController {

    private final InvestmentService investmentService;
    private final ProjectClient projectClient;
    private final UserWalletReconciliationService userWalletReconciliationService;

    /**
     * Lista las compras primarias del usuario autenticado.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Page<InvestmentResponse>>> myInvestments(
            Authentication auth,
            Pageable pageable) {
        Long userId = (Long) auth.getPrincipal();
        userWalletReconciliationService.reconcileIfNeeded(userId);
        Page<InvestmentResponse> page = investmentService.listForUser(userId, pageable)
                .map(InvestmentResponse::from);
        return ResponseEntity.ok(ApiResponse.success("OK", page));
    }

    /**
     * Total acumulado USDC + tier actual del usuario autenticado.
     */
    @GetMapping("/me/total")
    public ResponseEntity<ApiResponse<InvestmentTotalResponse>> myTotal(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        userWalletReconciliationService.reconcileIfNeeded(userId);
        return ResponseEntity.ok(ApiResponse.success(
                "OK", InvestmentTotalResponse.from(investmentService.getTotalForUser(userId))));
    }

    /**
     * Preview de compra: el frontend pasa el monto USDC que quiere invertir y
     * recibe el equivalente en LKN al precio vigente del proyecto + un flag
     * indicando si la inversión está habilitada (estado del proyecto).
     */
    @GetMapping("/preview")
    public ResponseEntity<ApiResponse<PreviewResponse>> preview(
            @RequestParam Long projectId,
            @RequestParam BigDecimal usdcAmount) {
        if (usdcAmount == null || usdcAmount.signum() <= 0) {
            return ResponseEntity.badRequest().body(ApiResponse.<PreviewResponse>builder()
                    .message("El monto debe ser mayor a cero")
                    .status(400)
                    .build());
        }

        ProjectClient.ProjectSnapshot snap = projectClient.fetch(projectId);
        if (snap == null) {
            return ResponseEntity.status(404).body(ApiResponse.<PreviewResponse>builder()
                    .message("Proyecto no encontrado")
                    .status(404)
                    .build());
        }

        boolean canInvest = "PRE_OPEN".equals(snap.state()) && "OPEN".equals(snap.roundState());
        String reason = canInvest ? null
                : "La ronda primaria ya no está abierta para este proyecto.";

        BigDecimal lknAmount = (snap.currentPrice() != null && snap.currentPrice().signum() > 0)
                ? usdcAmount.divide(snap.currentPrice(), 8, RoundingMode.DOWN)
                : BigDecimal.ZERO;

        return ResponseEntity.ok(ApiResponse.success("OK", PreviewResponse.builder()
                .projectId(projectId)
                .state(snap.state())
                .currentPrice(snap.currentPrice())
                .usdcAmount(usdcAmount)
                .lknAmount(lknAmount)
                .offeringContractAddress(snap.offeringContractAddress())
                .canInvest(canInvest)
                .reason(reason)
                .build()));
    }
}
