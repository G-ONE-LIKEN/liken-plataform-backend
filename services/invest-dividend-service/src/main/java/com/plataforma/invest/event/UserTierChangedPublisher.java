package com.plataforma.invest.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publica {@code user.tier_changed} cuando un usuario cruza un umbral de tier.
 * El consumer es {@code UserTierChangedConsumer} en user-service (ya existe).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserTierChangedPublisher {

    private static final String TOPIC = "user.tier_changed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(Long userId, String oldTier, String newTier) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", UUID.randomUUID().toString());
        payload.put("occurredAt", Instant.now().toString());
        payload.put("version", 1);
        payload.put("userId", userId);
        payload.put("oldTier", oldTier);
        payload.put("newTier", newTier);
        sendAfterCommit(TOPIC, String.valueOf(userId), payload);
        log.info("user.tier_changed publicado: userId={} {} → {}", userId, oldTier, newTier);
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
