package com.plataforma.user.event.producer;

import com.plataforma.user.event.dto.UserContextInvalidatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserContextEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TOPIC = "user.context_invalidated";

    public void invalidateContext(Long userId) {
        try {
            kafkaTemplate.send(TOPIC, String.valueOf(userId), new UserContextInvalidatedEvent(userId));
            log.info("Publicado {}: userId={}", TOPIC, userId);
        } catch (Exception e) {
            log.warn("Error publicando {}: userId={}: {}", TOPIC, userId, e.getMessage());
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
