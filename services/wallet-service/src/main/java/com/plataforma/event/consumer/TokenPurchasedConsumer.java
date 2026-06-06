package com.plataforma.event.consumer;

import com.plataforma.wallet.model.MovementType;
import com.plataforma.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

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
    public void consume(Map<String, Object> payload) {
        TokenPurchasedView event = TokenPurchasedView.from(payload);
        if (event.userId() != null) {
            recordNormal(event, event.userId());
            return;
        }
        if (event.walletAddress() != null && !event.walletAddress().isBlank()) {
            recordOrPending(event);
            return;
        }
        log.warn("Evento investment.token_purchased sin userId ni walletAddress. Descartando.");
    }

    private void recordNormal(TokenPurchasedView event, Long userId) {
        try {
            log.info("Procesando compra on-chain: usuario={}, usdc={}, lkn={}, txHash={}",
                    userId, event.usdcAmount(), event.lknAmount(), event.txHash());
            walletService.recordExternalMovement(
                    userId,
                    MovementType.TOKEN_PURCHASE,
                    event.usdcAmount(),
                    "Compra de " + event.lknAmount() + " LKN del proyecto "
                            + event.projectId() + " (tx " + event.txHash() + ")",
                    event.txHash(),
                    event.eventId()
            );
        } catch (Exception e) {
            log.error("Error procesando investment.token_purchased para usuario {}: {}",
                    userId, e.getMessage(), e);
        }
    }

    private void recordOrPending(TokenPurchasedView event) {
        var existing = walletService.findByWalletAddress(event.walletAddress());
        if (existing.isPresent()) {
            recordNormal(event, existing.get().getUserId());
        } else {
            log.warn("Compra on-chain sin usuario vinculado. Guardando como pending: wallet={} tx={}",
                    event.walletAddress(), event.txHash());
            walletService.recordPendingMovement(
                    event.walletAddress(),
                    MovementType.TOKEN_PURCHASE,
                    event.usdcAmount(),
                    "Compra de " + event.lknAmount() + " LKN del proyecto "
                            + event.projectId() + " (tx " + event.txHash() + ")",
                    event.txHash(),
                    event.eventId()
            );
        }
    }

    private record TokenPurchasedView(
            String eventId,
            Long userId,
            String walletAddress,
            Long projectId,
            BigDecimal usdcAmount,
            BigDecimal lknAmount,
            String txHash
    ) {
        static TokenPurchasedView from(Map<String, Object> payload) {
            return new TokenPurchasedView(
                    str(payload.get("eventId")),
                    toLong(payload.get("userId")),
                    str(payload.get("walletAddress")),
                    toLong(payload.get("projectId")),
                    toBigDecimal(payload.get("usdcAmount")),
                    toBigDecimal(payload.get("lknAmount")),
                    str(payload.get("txHash"))
            );
        }
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        String s = value.toString();
        return s.isBlank() ? null : Long.parseLong(s);
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof Number n) return new BigDecimal(n.toString());
        return new BigDecimal(value.toString());
    }
}
