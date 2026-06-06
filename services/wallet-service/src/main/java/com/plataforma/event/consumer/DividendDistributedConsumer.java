package com.plataforma.event.consumer;

import com.plataforma.event.dto.DividendsClaimedEvent;
import com.plataforma.wallet.model.MovementType;
import com.plataforma.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consume {@code dividends.claimed} (modelo PULL on-chain).
 *
 * <p>El holder retiró sus dividendos llamando {@code DividendDistributor.claimDividends()}
 * con su MetaMask. El Blockchain Service indexó el evento on-chain
 * {@code DividendsWithdrawn(holder, amount)}, resolvió {@code holder → userId} contra
 * user-service y publicó este evento. El consumer sólo registra el movimiento
 * contable; el USDC ya está en la wallet del usuario on-chain.
 *
 * <p>Reemplaza al consumer viejo de {@code dividends.distributed} (modelo push).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DividendDistributedConsumer {

    private final WalletService walletService;

    @KafkaListener(topics = "dividends.claimed", groupId = "wallet-service")
    public void consume(DividendsClaimedEvent event) {
        if (event.getUserId() == null) {
            log.warn("Evento dividends.claimed sin userId resuelto (wallet={}). " +
                    "Se descarta hasta que el lookup de wallet→user devuelva un valor",
                    event.getWalletAddress());
            return;
        }

        try {
            log.info("Procesando reclamo de dividendos: usuario={}, monto={}, txHash={}",
                    event.getUserId(), event.getAmount(), event.getTxHash());

            walletService.recordMovement(
                    event.getUserId(),
                    MovementType.DIVIDEND,
                    event.getAmount(),
                    "Dividendos reclamados on-chain (tx " + event.getTxHash() + ")",
                    event.getTxHash(),
                    event.getEventId()
            );
        } catch (Exception e) {
            log.error("Error procesando evento dividends.claimed para usuario {}: {}",
                    event.getUserId(), e.getMessage(), e);
        }
    }
}
