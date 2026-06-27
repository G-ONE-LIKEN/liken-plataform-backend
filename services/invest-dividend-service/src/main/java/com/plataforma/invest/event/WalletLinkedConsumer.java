package com.plataforma.invest.event;

import com.plataforma.invest.service.DividendService;
import com.plataforma.invest.service.InvestmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WalletLinkedConsumer {

    private final InvestmentService investmentService;
    private final DividendService dividendService;

    @KafkaListener(topics = "user.wallet_linked", groupId = "invest-dividend-service")
    public void consume(Map<String, Object> payload) {
        Long userId = toLong(payload.get("userId"));
        String walletAddress = str(payload.get("walletAddress"));

        if (userId == null || walletAddress == null || walletAddress.isBlank()) {
            log.warn("Evento user.wallet_linked incompleto para invest-dividend-service: {}", payload);
            return;
        }

        // Sin try/catch: las fallas van a retries + DLT (KafkaErrorHandlingConfig).
        int investments = investmentService.reconcileWalletLinked(userId, walletAddress);
        int claims = dividendService.reconcileWalletLinked(userId, walletAddress);
        log.info("Reconciliacion invest-dividend completa: userId={} wallet={} investments={} claims={}",
                userId, walletAddress, investments, claims);
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
