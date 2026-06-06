package com.plataforma.event.consumer;

import com.plataforma.event.dto.TokenPurchasedEvent;
import com.plataforma.wallet.model.MovementType;
import com.plataforma.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consume {@code investment.token_purchased} (publicado por el Blockchain Service al
 * indexar {@code OfferingContract.TokensPurchased}).
 *
 * <p>Registra el reflejo contable de la compra primaria. El USDC ya viajó
 * on-chain desde la wallet del inversor al treasury de la plataforma; este
 * movimiento {@link MovementType#TOKEN_PURCHASE} mantiene la trazabilidad off-chain.
 *
 * <p>Si el evento llega sin {@code userId} pero con {@code walletAddress},
 * intenta buscar la Wallet por walletAddress. Si existe, crea el movement normal;
 * si no, lo guarda como pending para reconciliar cuando el usuario vincule la wallet.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenPurchasedConsumer {

    private final WalletService walletService;

    @KafkaListener(topics = "investment.token_purchased", groupId = "wallet-service")
    public void consume(TokenPurchasedEvent event) {
        if (event.getUserId() != null) {
            recordNormal(event, event.getUserId());
            return;
        }
        if (event.getWalletAddress() != null && !event.getWalletAddress().isBlank()) {
            recordOrPending(event);
            return;
        }
        log.warn("Evento investment.token_purchased sin userId ni walletAddress. Descartando.");
    }

    private void recordNormal(TokenPurchasedEvent event, Long userId) {
        try {
            log.info("Procesando compra on-chain: usuario={}, usdc={}, lkn={}, txHash={}",
                    userId, event.getUsdcAmount(), event.getLknAmount(), event.getTxHash());
            walletService.recordMovement(
                    userId,
                    MovementType.TOKEN_PURCHASE,
                    event.getUsdcAmount(),
                    "Compra de " + event.getLknAmount() + " LKN del proyecto "
                            + event.getProjectId() + " (tx " + event.getTxHash() + ")",
                    event.getTxHash(),
                    event.getEventId()
            );
        } catch (Exception e) {
            log.error("Error procesando investment.token_purchased para usuario {}: {}",
                    userId, e.getMessage(), e);
        }
    }

    private void recordOrPending(TokenPurchasedEvent event) {
        var existing = walletService.findByWalletAddress(event.getWalletAddress());
        if (existing.isPresent()) {
            recordNormal(event, existing.get().getUserId());
        } else {
            log.warn("Compra on-chain sin usuario vinculado. Guardando como pending: wallet={} tx={}",
                    event.getWalletAddress(), event.getTxHash());
            walletService.recordPendingMovement(
                    event.getWalletAddress(),
                    MovementType.TOKEN_PURCHASE,
                    event.getUsdcAmount(),
                    "Compra de " + event.getLknAmount() + " LKN del proyecto "
                            + event.getProjectId() + " (tx " + event.getTxHash() + ")",
                    event.getTxHash(),
                    event.getEventId()
            );
        }
    }
}
