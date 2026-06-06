package com.plataforma.event.consumer;

import com.plataforma.event.dto.WalletRefundEvent;
import com.plataforma.wallet.model.MovementType;
import com.plataforma.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consume {@code wallet.refund} (publicado por el Blockchain Service al indexar
 * {@code OfferingContract.Refunded}).
 *
 * <p>Una ronda primaria falló (deadline sin soft cap) y el inversor recuperó su
 * USDC llamando {@code refund()}. El wallet-service registra el reflejo contable
 * como {@link MovementType#REFUND}.
 *
 * <p>Si el evento llega sin {@code userId} pero con {@code walletAddress},
 * intenta buscar la Wallet por walletAddress. Si existe, crea el movement normal;
 * si no, lo guarda como pending para reconciliar cuando el usuario vincule la wallet.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletRefundConsumer {

    private final WalletService walletService;

    @KafkaListener(topics = "wallet.refund", groupId = "wallet-service")
    public void consume(WalletRefundEvent event) {
        if (event.getUserId() != null) {
            recordNormal(event, event.getUserId());
            return;
        }
        if (event.getWalletAddress() != null && !event.getWalletAddress().isBlank()) {
            recordOrPending(event);
            return;
        }
        log.warn("Evento wallet.refund sin userId ni walletAddress. Descartando.");
    }

    private void recordNormal(WalletRefundEvent event, Long userId) {
        try {
            log.info("Procesando refund on-chain: usuario={}, monto={}, projectId={}, txHash={}",
                    userId, event.getUsdcAmount(), event.getProjectId(), event.getTxHash());
            walletService.recordMovement(
                    userId,
                    MovementType.REFUND,
                    event.getUsdcAmount(),
                    "Refund proyecto " + event.getProjectId() + " (tx " + event.getTxHash() + ")",
                    event.getTxHash(),
                    event.getEventId()
            );
        } catch (Exception e) {
            log.error("Error procesando wallet.refund para usuario {}: {}",
                    userId, e.getMessage(), e);
        }
    }

    private void recordOrPending(WalletRefundEvent event) {
        var existing = walletService.findByWalletAddress(event.getWalletAddress());
        if (existing.isPresent()) {
            recordNormal(event, existing.get().getUserId());
        } else {
            log.warn("Refund sin usuario vinculado. Guardando como pending: wallet={} tx={}",
                    event.getWalletAddress(), event.getTxHash());
            walletService.recordPendingMovement(
                    event.getWalletAddress(),
                    MovementType.REFUND,
                    event.getUsdcAmount(),
                    "Refund proyecto " + event.getProjectId() + " (tx " + event.getTxHash() + ")",
                    event.getTxHash(),
                    event.getEventId()
            );
        }
    }
}
