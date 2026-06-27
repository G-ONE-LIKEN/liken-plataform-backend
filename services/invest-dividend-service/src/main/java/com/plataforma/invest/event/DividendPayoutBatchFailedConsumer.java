package com.plataforma.invest.event;

import com.plataforma.invest.service.DividendBatchTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consume {@code dividends.payout_batch_failed}: el batch entero fue abortado
 * (tipicamente saldo insuficiente del signer). Devuelve TODO el monto a
 * pending del proyecto y marca el batch como FAILED.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DividendPayoutBatchFailedConsumer {

    private final DividendBatchTracker tracker;

    @KafkaListener(topics = "dividends.payout_batch_failed", groupId = "invest-dividend-service")
    public void consume(Map<String, Object> payload) {
        try {
            String batchId = str(payload.get("batchId"));
            Long projectId = toLong(payload.get("projectId"));
            String reason = str(payload.get("reason"));

            if (batchId == null || projectId == null) {
                log.warn("dividends.payout_batch_failed incompleto, ignoro: {}", payload);
                return;
            }
            tracker.recordBatchFailed(batchId, projectId, reason);
        } catch (Exception e) {
            log.error("Error procesando dividends.payout_batch_failed: {}", payload, e);
            throw e;
        }
    }

    private static String str(Object v) { return v == null ? null : v.toString(); }
    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(v.toString());
    }
}
