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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletRefundConsumer {

    private final WalletService walletService;

    @KafkaListener(topics = "wallet.refund", groupId = "wallet-service")
    public void consume(WalletRefundEvent event) {
        if (event.getUserId() == null) {
            log.warn("Evento wallet.refund sin userId resuelto (wallet={}). " +
                    "Se descarta hasta que el lookup de wallet→user devuelva un valor",
                    event.getWalletAddress());
            return;
        }

        try {
            log.info("Procesando refund on-chain: usuario={}, monto={}, projectId={}, txHash={}",
                    event.getUserId(), event.getUsdcAmount(), event.getProjectId(), event.getTxHash());

            walletService.recordMovement(
                    event.getUserId(),
                    MovementType.REFUND,
                    event.getUsdcAmount(),
                    "Refund proyecto " + event.getProjectId() + " (tx " + event.getTxHash() + ")",
                    event.getTxHash(),
                    event.getEventId()
            );
        } catch (Exception e) {
            log.error("Error procesando wallet.refund para usuario {}: {}",
                    event.getUserId(), e.getMessage(), e);
        }
    }
}
