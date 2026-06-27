package com.plataforma.invest.event;

import com.plataforma.invest.service.DividendBatchTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Consume {@code dividends.paid} publicado por blockchain-service tras una
 * transferencia USDC confirmada on-chain. Persiste el {@link
 * com.plataforma.invest.model.DividendPayout}, resta el monto del in_flight
 * del acumulador, y cierra el batch si fue el ultimo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DividendPaidConsumer {

    private final DividendBatchTracker tracker;

    @KafkaListener(topics = "dividends.paid", groupId = "invest-dividend-service")
    public void consume(Map<String, Object> payload) {
        try {
            String batchId = str(payload.get("batchId"));
            String payoutEventId = str(payload.get("payoutEventId"));
            Long projectId = toLong(payload.get("projectId"));
            Long userId = toLong(payload.get("userId"));
            String wallet = str(payload.get("walletAddress"));
            BigDecimal amount = bigDecimal(payload.get("amount"));
            String txHash = str(payload.get("txHash"));
            Long blockNumber = toLong(payload.get("blockNumber"));

            if (batchId == null || payoutEventId == null || projectId == null
                    || wallet == null || amount.signum() <= 0) {
                log.warn("dividends.paid incompleto, ignoro: {}", payload);
                return;
            }

            tracker.recordPaid(batchId, payoutEventId, projectId, userId, wallet,
                    amount, txHash, blockNumber);
        } catch (Exception e) {
            log.error("Error procesando dividends.paid: {}", payload, e);
            throw e;
        }
    }

    private static String str(Object v) { return v == null ? null : v.toString(); }
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
