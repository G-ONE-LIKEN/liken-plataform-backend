package com.plataforma.invest.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

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
        kafkaTemplate.send(TOPIC, String.valueOf(userId), payload);
        log.info("user.tier_changed publicado: userId={} {} → {}", userId, oldTier, newTier);
    }
}
