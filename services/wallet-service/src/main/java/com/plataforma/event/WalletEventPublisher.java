package com.plataforma.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WalletEventPublisher {

    private static final String TOPIC_CREDITED = "wallet.credited";
    private static final String TOPIC_DEBITED  = "wallet.debited";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishFunded(Long userId, BigDecimal amount, BigDecimal newBalance) {
        Map<String, Object> payload = Map.of(
                "eventId",    UUID.randomUUID().toString(),
                "occurredAt", Instant.now().toString(),
                "version",    1,
                "userId",     userId,
                "amount",     amount,
                "newBalance", newBalance
        );
        kafkaTemplate.send(TOPIC_CREDITED, String.valueOf(userId), payload);
        log.debug("Evento wallet.credited publicado para usuario {}", userId);
    }

    public void publishDebited(Long userId, BigDecimal amount, BigDecimal newBalance) {
        Map<String, Object> payload = Map.of(
                "eventId",    UUID.randomUUID().toString(),
                "occurredAt", Instant.now().toString(),
                "version",    1,
                "userId",     userId,
                "amount",     amount,
                "newBalance", newBalance
        );
        kafkaTemplate.send(TOPIC_DEBITED, String.valueOf(userId), payload);
        log.debug("Evento wallet.debited publicado para usuario {}", userId);
    }
}
