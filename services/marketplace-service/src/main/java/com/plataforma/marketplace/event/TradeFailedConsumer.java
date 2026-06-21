package com.plataforma.marketplace.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataforma.marketplace.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeFailedConsumer {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "blockchain.trade_failed", groupId = "marketplace-service")
    public void consume(java.util.Map<String, Object> payload) {
        log.info("Recibido evento blockchain.trade_failed: {}", payload);
        try {
            Object orderIdObj = payload.get("orderId");
            if (orderIdObj != null) {
                Long orderId = Long.valueOf(orderIdObj.toString());
                orderService.handleTradeFailed(orderId);
            } else {
                log.warn("Evento blockchain.trade_failed sin orderId válido: {}", payload);
            }
        } catch (Exception e) {
            log.error("Error procesando blockchain.trade_failed", e);
        }
    }
}
