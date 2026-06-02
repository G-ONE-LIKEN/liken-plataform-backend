package com.plataforma.user.event.producer;

import com.plataforma.user.model.DeveloperStatus;
import com.plataforma.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publica eventos del ciclo de vida del rol DEVELOPER para que el
 * notification-service emita notificaciones a admins y al usuario.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeveloperEventProducer {

    private static final String TOPIC_REGISTERED      = "user.developer_registered";
    private static final String TOPIC_STATUS_CHANGED  = "user.developer_status_changed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishRegistered(User user) {
        Map<String, Object> payload = baseEvent(user);
        try {
            kafkaTemplate.send(TOPIC_REGISTERED, String.valueOf(user.getId()), payload);
            log.info("Publicado {}: userId={}", TOPIC_REGISTERED, user.getId());
        } catch (Exception e) {
            log.warn("Error publicando {}: {}", TOPIC_REGISTERED, e.getMessage());
        }
    }

    public void publishStatusChanged(User user, DeveloperStatus status) {
        Map<String, Object> payload = baseEvent(user);
        payload.put("status", status != null ? status.name() : null);
        try {
            kafkaTemplate.send(TOPIC_STATUS_CHANGED, String.valueOf(user.getId()), payload);
            log.info("Publicado {}: userId={} status={}", TOPIC_STATUS_CHANGED, user.getId(), status);
        } catch (Exception e) {
            log.warn("Error publicando {}: {}", TOPIC_STATUS_CHANGED, e.getMessage());
        }
    }

    private Map<String, Object> baseEvent(User user) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId",    UUID.randomUUID().toString());
        payload.put("occurredAt", Instant.now().toString());
        payload.put("version",    1);
        payload.put("userId",     user.getId());
        payload.put("email",      user.getEmail());
        payload.put("firstName",  user.getFirstName());
        payload.put("lastName",   user.getLastName());
        return payload;
    }
}
