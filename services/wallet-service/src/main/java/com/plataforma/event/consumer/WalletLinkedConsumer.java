package com.plataforma.event.consumer;

import com.plataforma.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consume {@code user.wallet_linked} (publicado por el user-service cuando un
 * usuario vincula su wallet on-chain).
 *
 * <p>Reconcilia todos los {@link com.plataforma.wallet.model.PendingWalletMovement}
 * de esa walletAddress creando movimientos reales y actualizando el balance.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletLinkedConsumer {

    private final WalletService walletService;

    @KafkaListener(topics = "user.wallet_linked", groupId = "wallet-service")
    public void consume(Map<String, Object> payload) {
        Number userIdNum = (Number) payload.get("userId");
        String walletAddress = (String) payload.get("walletAddress");

        if (userIdNum == null || walletAddress == null || walletAddress.isBlank()) {
            log.warn("Evento user.wallet_linked incompleto: userId={}, wallet={}", userIdNum, walletAddress);
            return;
        }

        Long userId = userIdNum.longValue();
        try {
            log.info("Reconciliando movimientos pendientes para userId={} wallet={}", userId, walletAddress);
            walletService.reconcilePendingMovements(userId, walletAddress);
        } catch (Exception e) {
            log.error("Error reconciliando pending movements para userId={}: {}", userId, e.getMessage(), e);
        }
    }
}
