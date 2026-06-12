package com.plataforma.invest.event;

import com.plataforma.invest.service.DividendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Consume {@code dividends.claimed} publicado por el Blockchain Service desde
 * el evento on-chain {@code DividendDistributor.DividendsWithdrawn}.
 *
 * <p>Registra el reclamo en {@code dividend_claim} para historial. El movimiento
 * contable en el wallet lo registra {@code wallet-service} consumiendo el mismo
 * topic — ambos consumers son independientes y usan el mismo eventId para
 * idempotencia local.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DividendsClaimedConsumer {

    private final DividendService dividendService;

    /** Sin try/catch: las fallas van a retries + DLT (KafkaErrorHandlingConfig). */
    @KafkaListener(topics = "dividends.claimed", groupId = "invest-dividend-service")
    public void consume(Map<String, Object> payload) {
        String eventId = str(payload.get("eventId"));
        String walletAddress = str(payload.get("walletAddress"));
        Long userId = toLong(payload.get("userId"));
        BigDecimal amount = bigDecimal(payload.get("amount"));
        String txHash = str(payload.get("txHash"));
        Long blockNumber = toLong(payload.get("blockNumber"));

        dividendService.recordClaim(eventId, userId, walletAddress, amount, txHash, blockNumber);
    }

    private static String str(Object v) {
        return v == null ? null : v.toString();
    }

    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        String s = v.toString();
        return s.isBlank() ? null : Long.parseLong(s);
    }

    private static BigDecimal bigDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof Number n) return new BigDecimal(n.toString());
        return new BigDecimal(v.toString());
    }
}
