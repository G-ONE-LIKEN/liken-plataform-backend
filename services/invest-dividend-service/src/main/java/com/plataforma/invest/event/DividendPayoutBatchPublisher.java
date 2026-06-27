package com.plataforma.invest.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Publica {@code dividends.payout_batch_requested}: pide a blockchain-service
 * que transfiera USDC a cada uno de los holders del batch. Consumer:
 * {@code DividendPayoutBatchConsumer} en blockchain-service.
 *
 * <p>{@code sendAfterCommit}: el envio a Kafka recien sucede cuando la tx de
 * DB (que persistio el {@code DividendBatch} y movio el acumulador a
 * in_flight) commitea.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DividendPayoutBatchPublisher {

    private static final String TOPIC = "dividends.payout_batch_requested";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public record PayoutItem(Long userId, String walletAddress, BigDecimal amount, String payoutEventId) {}

    public void publish(String batchId, Long projectId, List<PayoutItem> payouts) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", batchId);
        payload.put("occurredAt", Instant.now().toString());
        payload.put("version", 1);
        payload.put("batchId", batchId);
        payload.put("projectId", projectId);
        payload.put("payouts", payouts.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", p.userId());
            m.put("walletAddress", p.walletAddress());
            m.put("amount", p.amount().toPlainString());
            m.put("payoutEventId", p.payoutEventId());
            return m;
        }).toList());

        sendAfterCommit(String.valueOf(projectId), payload);
    }

    private void sendAfterCommit(String key, Object payload) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    kafkaTemplate.send(TOPIC, key, payload);
                }
            });
        } else {
            kafkaTemplate.send(TOPIC, key, payload);
        }
    }
}
