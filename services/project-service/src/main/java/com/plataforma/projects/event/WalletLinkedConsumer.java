package com.plataforma.projects.event;

import com.plataforma.projects.service.UserHoldingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WalletLinkedConsumer {

    private final UserHoldingService userHoldingService;

    @KafkaListener(topics = "user.wallet_linked", groupId = "service-projects")
    public void consume(Map<String, Object> payload) {
        Long userId = toLong(payload.get("userId"));
        String walletAddress = str(payload.get("walletAddress"));

        if (userId == null || walletAddress == null || walletAddress.isBlank()) {
            log.warn("Evento user.wallet_linked incompleto para project-service: {}", payload);
            return;
        }

        try {
            int reconciled = userHoldingService.reconcileWalletLinked(userId, walletAddress);
            log.info("Reconciliacion project-service completa: userId={} wallet={} holdings={}",
                    userId, walletAddress, reconciled);
        } catch (Exception e) {
            log.error("Error reconciliando wallet vinculada en project-service: payload={}", payload, e);
        }
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        String text = value.toString();
        return text.isBlank() ? null : Long.parseLong(text);
    }
}
