package com.plataforma.marketplace.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Publica {@code marketplace.order_matched} cuando se concreta una transacción P2P.
 *
 * <p>Payload compatible con los consumidores ya existentes:
 * <ul>
 *   <li>{@code wallet-service} (OrderMatchedConsumer → registra movimientos P2P_SALE/P2P_PURCHASE)</li>
 *   <li>{@code project-service} (UserHoldingEventConsumer → actualiza holdings vendedor/comprador)</li>
 * </ul>
 *
 * <p>El payload sigue el modelo canónico de eventos (ADR-0012):
 * {@code eventId}, {@code occurredAt}, {@code version} + campos de negocio.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMatchedPublisher {

    private static final String TOPIC = "marketplace.order_matched";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(Long sellerId, Long buyerId, Long projectId,
                        BigDecimal tokensAmount, BigDecimal totalPrice, Long orderId) {
        String eventId = UUID.randomUUID().toString();

        Map<String, Object> event = Map.of(
                "eventId", eventId,
                "occurredAt", Instant.now().toString(),
                "version", 1,
                "sellerId", sellerId,
                "buyerId", buyerId,
                "projectId", projectId,
                "tokenCount", tokensAmount.intValue(),
                "price", totalPrice,
                "orderId", String.valueOf(orderId)
        );

        kafkaTemplate.send(TOPIC, String.valueOf(orderId), event);
        log.info("Evento {} publicado: sellerId={} buyerId={} projectId={} tokens={} price={}",
                TOPIC, sellerId, buyerId, projectId, tokensAmount, totalPrice);
    }
}
