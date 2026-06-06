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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenPurchasedConsumer {

    private final WalletService walletService;

    @KafkaListener(topics = "investment.token_purchased", groupId = "wallet-service")
    public void consume(TokenPurchasedEvent event) {
        if (event.getUserId() == null) {
            log.warn("Evento investment.token_purchased sin userId resuelto (wallet={}). " +
                    "Se descarta hasta que el lookup de wallet→user devuelva un valor",
                    event.getWalletAddress());
            return;
        }

        try {
            log.info("Procesando compra on-chain: usuario={}, usdc={}, lkn={}, txHash={}",
                    event.getUserId(), event.getUsdcAmount(), event.getLknAmount(), event.getTxHash());

            walletService.recordMovement(
                    event.getUserId(),
                    MovementType.TOKEN_PURCHASE,
                    event.getUsdcAmount(),
                    "Compra de " + event.getLknAmount() + " LKN del proyecto "
                            + event.getProjectId() + " (tx " + event.getTxHash() + ")",
                    event.getTxHash(),
                    event.getEventId()
            );
        } catch (Exception e) {
            log.error("Error procesando investment.token_purchased para usuario {}: {}",
                    event.getUserId(), e.getMessage(), e);
        }
    }
}
