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
 * Publica {@code marketplace.trade_settled} una vez confirmada la liquidación en la blockchain.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeSettledPublisher {

    private static final String TOPIC = "marketplace.trade_settled";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(Long sellerId, Long buyerId, Long projectId,
                        BigDecimal tokensAmount, BigDecimal totalPrice, Long orderId, String txHash) {
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
                "orderId", String.valueOf(orderId),
                "txHash", txHash != null ? txHash : ""
        );

        kafkaTemplate.send(TOPIC, String.valueOf(orderId), event);
        log.info("Evento {} publicado: sellerId={} buyerId={} projectId={} tokens={} price={} txHash={}",
                TOPIC, sellerId, buyerId, projectId, tokensAmount, totalPrice, txHash);
    }
}
