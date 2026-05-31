package com.plataforma.event.consumer;

import com.plataforma.event.dto.TokenPurchasedEvent;
import com.plataforma.wallet.model.MovementType;
import com.plataforma.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenPurchasedConsumer {

    private final WalletService walletService;

    @KafkaListener(topics = "investment.token_purchased", groupId = "wallet-service")
    public void consume(TokenPurchasedEvent event) {
        try {
            log.info("Procesando compra de tokens: usuario={}, monto={}, tx={}",
                    event.getUserId(), event.getTotalPrice(), event.getTransactionId());

            walletService.recordMovement(
                    event.getUserId(),
                    MovementType.TOKEN_PURCHASE,
                    event.getTotalPrice(),
                    "Compra de " + event.getTokenCount() + " tokens del proyecto " + event.getProjectId(),
                    event.getTransactionId(),
                    event.getEventId()
            );
        } catch (Exception e) {
            log.error("Error procesando evento investment.token_purchased para usuario {}: {}",
                    event.getUserId(), e.getMessage(), e);
        }
    }
}
