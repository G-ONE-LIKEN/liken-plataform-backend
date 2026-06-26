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
import java.util.Map;

/**
 * Publica {@code dividends.deposit_requested}: pedido al blockchain-service de
 * depositar USDC al DividendDistributor para repartir entre los holders.
 * El consumer es {@code DividendDepositConsumer} en blockchain-service.
 *
 * <p>Se publica con {@code afterCommit} para garantizar que el acumulador haya
 * quedado persistido (in-flight movido) antes de pedirle al chain que deposite.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DividendDepositRequestedPublisher {

    private static final String TOPIC = "dividends.deposit_requested";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(Long projectId, BigDecimal amountUsdc, String eventId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", eventId);
        payload.put("occurredAt", Instant.now().toString());
        payload.put("version", 1);
        payload.put("projectId", projectId);
        payload.put("amountUsdc", amountUsdc.toPlainString());
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
