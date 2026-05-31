package com.plataforma.user.event.consumer;

import com.plataforma.user.event.dto.UserTierChangedEvent;
import com.plataforma.user.event.producer.UserContextEventProducer;
import com.plataforma.user.model.User;
import com.plataforma.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consume {@code user.tier_changed} publicado por invest-dividend-service
 * para mantener actualizado el campo {@code tier} en la tabla {@code users}
 * (ver DD013).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserTierChangedConsumer {

    private final UserRepository userRepository;
    private final UserContextEventProducer contextEventProducer;

    @KafkaListener(topics = "user.tier_changed", groupId = "user-service")
    @Transactional
    public void onTierChanged(UserTierChangedEvent event) {
        try {
            User user = userRepository.findById(event.getUserId()).orElse(null);
            if (user == null) {
                log.warn("Recibido user.tier_changed para usuario inexistente {}", event.getUserId());
                return;
            }
            user.setTier(event.getNewTier());
            userRepository.save(user);
            log.info("Tier actualizado: usuario={} {} → {}",
                    event.getUserId(), event.getOldTier(), event.getNewTier());
            contextEventProducer.invalidateContext(event.getUserId());
        } catch (Exception e) {
            log.error("Error procesando user.tier_changed para usuario {}: {}",
                    event.getUserId(), e.getMessage(), e);
        }
    }
}
