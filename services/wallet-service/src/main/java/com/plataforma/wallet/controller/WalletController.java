package com.plataforma.wallet.controller;

import com.plataforma.shared.dto.ApiResponse;
import com.plataforma.wallet.dto.DepositRequest;
import com.plataforma.wallet.dto.MovementResponse;
import com.plataforma.wallet.dto.WalletResponse;
import com.plataforma.wallet.dto.WithdrawRequest;
import com.plataforma.wallet.model.WalletMovement;
import com.plataforma.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    /**
     * Devuelve la billetera del usuario autenticado.
     * Si no tiene una, se crea en el momento (lazy).
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<WalletResponse>> getMyWallet(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        WalletResponse response = WalletResponse.from(walletService.getOrCreateWallet(userId));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Historial de movimientos paginado del usuario autenticado.
     */
    @GetMapping("/me/movements")
    public ResponseEntity<ApiResponse<Page<MovementResponse>>> getMovements(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = (Long) auth.getPrincipal();
        Page<MovementResponse> movements = walletService
                .getMovements(userId, PageRequest.of(page, size))
                .map(MovementResponse::from);
        return ResponseEntity.ok(ApiResponse.success(movements));
    }

    /**
     * Deposita fondos en la billetera del usuario autenticado.
     */
    @PostMapping("/deposit")
    public ResponseEntity<ApiResponse<MovementResponse>> deposit(
            Authentication auth,
            @RequestBody @Valid DepositRequest request) {
        Long userId = (Long) auth.getPrincipal();
        WalletMovement movement = walletService.deposit(
                userId, request.getAmount(), request.getDescription(), null);
        return ResponseEntity.ok(ApiResponse.success(MovementResponse.from(movement)));
    }

    /**
     * Retira fondos de la billetera del usuario autenticado.
     * Devuelve 422 si el saldo es insuficiente.
     */
    @PostMapping("/withdraw")
    public ResponseEntity<ApiResponse<MovementResponse>> withdraw(
            Authentication auth,
            @RequestBody @Valid WithdrawRequest request) {
        Long userId = (Long) auth.getPrincipal();
        WalletMovement movement = walletService.withdraw(
                userId, request.getAmount(), request.getDescription(), null);
        return ResponseEntity.ok(ApiResponse.success(MovementResponse.from(movement)));
    }
}
