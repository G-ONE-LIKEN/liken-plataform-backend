package com.plataforma.user.event.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publica el evento {@code user.wallet_linked} cuando un usuario vincula
 * su wallet on-chain. El wallet-service consume este evento para reconciliar
 * movimientos pendientes que llegaron sin {@code userId} resuelto.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletLinkedEventProducer {

    private static final String TOPIC_WALLET_LINKED = "user.wallet_linked";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(Long userId, String walletAddress) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId",       UUID.randomUUID().toString());
        payload.put("occurredAt",    Instant.now().toString());
        payload.put("version",       1);
        payload.put("userId",        userId);
        payload.put("walletAddress", walletAddress);
        try {
            sendAfterCommit(TOPIC_WALLET_LINKED, String.valueOf(userId), payload);
            log.info("Publicado {}: userId={} wallet={}", TOPIC_WALLET_LINKED, userId, walletAddress);
        } catch (Exception e) {
            log.warn("Error publicando {}: {}", TOPIC_WALLET_LINKED, e.getMessage());
        }
    }

    private void sendAfterCommit(String topic, String key, Object payload) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    kafkaTemplate.send(topic, key, payload);
                }
            });
        } else {
            kafkaTemplate.send(topic, key, payload);
        }
    }
}
