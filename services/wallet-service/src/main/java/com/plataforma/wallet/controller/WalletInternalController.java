package com.plataforma.wallet.controller;

import com.plataforma.wallet.dto.WalletResponse;
import com.plataforma.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/wallets")
@RequiredArgsConstructor
public class WalletInternalController {

    private final WalletService walletService;

    @GetMapping("/{userId}/balance")
    public ResponseEntity<WalletResponse> getWalletBalance(@PathVariable Long userId) {
        WalletResponse response = WalletResponse.from(walletService.getOrCreateWallet(userId));
        return ResponseEntity.ok(response);
    }
}
