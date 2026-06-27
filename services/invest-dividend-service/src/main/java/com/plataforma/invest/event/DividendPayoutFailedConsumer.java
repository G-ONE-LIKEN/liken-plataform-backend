package com.plataforma.invest.event;

import com.plataforma.invest.service.DividendBatchTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Consume {@code dividends.paid_failed}: una transferencia individual fallo
 * (ej. exceeds max payout cap, revert on-chain). Devuelve el monto al pending
 * del proyecto via {@link DividendBatchTracker#recordFailed}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DividendPayoutFailedConsumer {

    private final DividendBatchTracker tracker;

    @KafkaListener(topics = "dividends.paid_failed", groupId = "invest-dividend-service")
    public void consume(Map<String, Object> payload) {
        try {
            String batchId = str(payload.get("batchId"));
            Long projectId = toLong(payload.get("projectId"));
            BigDecimal amount = bigDecimal(payload.get("amount"));
            String reason = str(payload.get("reason"));

            if (batchId == null || projectId == null || amount.signum() <= 0) {
                log.warn("dividends.paid_failed incompleto, ignoro: {}", payload);
                return;
            }
            tracker.recordFailed(batchId, projectId, amount, reason);
        } catch (Exception e) {
            log.error("Error procesando dividends.paid_failed: {}", payload, e);
            throw e;
        }
    }

    private static String str(Object v) { return v == null ? null : v.toString(); }
    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(v.toString());
    }
    private static BigDecimal bigDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof Number n) return new BigDecimal(n.toString());
        return new BigDecimal(v.toString());
    }
}
