package com.plataforma.gateway.event;

import com.plataforma.gateway.service.UserContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserContextInvalidatedConsumer {

    private final UserContextService userContextService;

    @KafkaListener(topics = "user.context_invalidated", groupId = "api-gateway")
    public void onContextInvalidated(UserContextInvalidatedEvent event) {
        userContextService.invalidate(event.getUserId());
        log.debug("Cache invalidado para userId={}", event.getUserId());
    }
}
