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
 * {@code DividendsWithdrawn(holder, amount)} y publicó este evento.
 *
 * <p>Si el evento llega sin {@code userId} pero con {@code walletAddress},
 * intenta buscar la Wallet por walletAddress. Si existe, crea el movement normal;
 * si no, lo guarda como pending para reconciliar cuando el usuario vincule la wallet.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DividendDistributedConsumer {

    private final WalletService walletService;

    @KafkaListener(topics = "dividends.claimed", groupId = "wallet-service")
    public void consume(DividendsClaimedEvent event) {
        if (event.getUserId() != null) {
            recordNormal(event, event.getUserId());
            return;
        }
        if (event.getWalletAddress() != null && !event.getWalletAddress().isBlank()) {
            recordOrPending(event);
            return;
        }
        log.warn("Evento dividends.claimed sin userId ni walletAddress. Descartando.");
    }

    private void recordNormal(DividendsClaimedEvent event, Long userId) {
        try {
            log.info("Procesando reclamo de dividendos: usuario={}, monto={}, txHash={}",
                    userId, event.getAmount(), event.getTxHash());
            walletService.recordMovement(
                    userId,
                    MovementType.DIVIDEND,
                    event.getAmount(),
                    "Dividendos reclamados on-chain (tx " + event.getTxHash() + ")",
                    event.getTxHash(),
                    event.getEventId()
            );
        } catch (Exception e) {
            log.error("Error procesando evento dividends.claimed para usuario {}: {}",
                    userId, e.getMessage(), e);
        }
    }

    private void recordOrPending(DividendsClaimedEvent event) {
        var existing = walletService.findByWalletAddress(event.getWalletAddress());
        if (existing.isPresent()) {
            recordNormal(event, existing.get().getUserId());
        } else {
            log.warn("Dividendo sin usuario vinculado. Guardando como pending: wallet={} tx={}",
                    event.getWalletAddress(), event.getTxHash());
            walletService.recordPendingMovement(
                    event.getWalletAddress(),
                    MovementType.DIVIDEND,
                    event.getAmount(),
                    "Dividendos reclamados on-chain (tx " + event.getTxHash() + ")",
                    event.getTxHash(),
                    event.getEventId()
            );
        }
    }
}
