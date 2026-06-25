package com.plataforma.user.event.producer;

import com.plataforma.user.model.User;
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
 * Publica el evento de registro de un usuario nuevo para que el
 * notification-service envie un email de bienvenida.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegisteredEventProducer {

    private static final String TOPIC_REGISTERED = "user.registered";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishRegistered(User user) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId",    UUID.randomUUID().toString());
        payload.put("occurredAt", Instant.now().toString());
        payload.put("version",    1);
        payload.put("userId",     user.getId());
        payload.put("email",      user.getEmail());
        payload.put("emailVerified", user.isEmailVerified());
        payload.put("firstName",  user.getFirstName());
        payload.put("lastName",   user.getLastName());
        payload.put("role",       user.getRole() != null ? user.getRole().getName() : null);
        try {
            sendAfterCommit(TOPIC_REGISTERED, String.valueOf(user.getId()), payload);
            log.info("Publicado {}: userId={}", TOPIC_REGISTERED, user.getId());
        } catch (Exception e) {
            log.warn("Error publicando {}: {}", TOPIC_REGISTERED, e.getMessage());
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
