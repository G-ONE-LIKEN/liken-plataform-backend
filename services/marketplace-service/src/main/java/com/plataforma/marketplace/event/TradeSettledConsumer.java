package com.plataforma.marketplace.event;

import com.plataforma.marketplace.model.ProcessedEvent;
import com.plataforma.marketplace.repository.ProcessedEventRepository;
import com.plataforma.marketplace.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Consume {@code blockchain.trade_settled} para finalizar la orden y registrar el Trade definitivo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeSettledConsumer {

    private final OrderService orderService;
    private final ProcessedEventRepository processedEventRepository;

    @KafkaListener(topics = "blockchain.trade_settled", groupId = "marketplace-service")
    public void consume(Map<String, Object> payload) {
        try {
            String eventId = str(payload.get("eventId"));

            // Idempotencia.
            if (eventId != null && processedEventRepository.existsByEventId(eventId)) {
                log.debug("Evento {} ya procesado, ignorando (idempotencia)", eventId);
                return;
            }

            Long orderId = toLong(payload.get("orderId"));
            BigDecimal tokenCount = toBigDecimal(payload.get("tokenCount"));
            BigDecimal price = toBigDecimal(payload.get("price"));
            BigDecimal feeAmount = toBigDecimal(payload.get("feeAmount"));
            String txHash = str(payload.get("txHash"));
            Long buyerId = toLong(payload.get("buyerId"));

            if (orderId == null) {
                log.warn("blockchain.trade_settled con datos incompletos: {}", payload);
                return;
            }

            log.info("[event] blockchain.trade_settled orderId={} buyerId={} tokenCount={} price={} fee={} txHash={}",
                    orderId, buyerId, tokenCount, price, feeAmount, txHash);

            orderService.processTradeSettled(orderId, buyerId, tokenCount, price, feeAmount, txHash, eventId);

            // Marcar como procesado.
            if (eventId != null) {
                processedEventRepository.save(ProcessedEvent.builder()
                        .eventId(eventId)
                        .topic("blockchain.trade_settled")
                        .processedAt(LocalDateTime.now())
                        .build());
            }
        } catch (Exception e) {
            log.error("Error procesando blockchain.trade_settled: {}", payload, e);
        }
    }

    private static String str(Object v) {
        return v == null ? null : v.toString();
    }

    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        String s = v.toString();
        return s.isBlank() ? null : Long.parseLong(s);
    }

    private static BigDecimal toBigDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof Number n) return new BigDecimal(n.toString());
        return new BigDecimal(v.toString());
    }
}
