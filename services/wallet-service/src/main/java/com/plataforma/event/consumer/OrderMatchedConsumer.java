package com.plataforma.event.consumer;

import com.plataforma.event.dto.OrderMatchedEvent;
import com.plataforma.wallet.model.MovementType;
import com.plataforma.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMatchedConsumer {

    private final WalletService walletService;

    @KafkaListener(topics = "marketplace.trade_settled", groupId = "wallet-service")
    public void consume(OrderMatchedEvent event) {
        try {
            log.info("Procesando orden P2P liquidada: vendedor={}, comprador={}, precio={}, orderId={}",
                    event.getSellerId(), event.getBuyerId(), event.getPrice(), event.getOrderId());

            // Un evento marketplace.trade_settled genera DOS movimientos (vendedor + comprador).
            // Cada movimiento necesita su propio eventId unico para no chocar con el UNIQUE
            // INDEX. Derivamos sub-IDs del eventId original sufijando el rol.
            String sellEventId = event.getEventId() != null ? event.getEventId() + ":seller" : null;
            String buyEventId  = event.getEventId() != null ? event.getEventId() + ":buyer"  : null;

            // Acreditar al vendedor
            walletService.recordExternalMovement(
                    event.getSellerId(),
                    MovementType.P2P_SALE,
                    event.getPrice(),
                    "Venta de " + event.getTokenCount() + " tokens del proyecto " + event.getProjectId(),
                    event.getOrderId(),
                    sellEventId
            );

            // Debitar al comprador
            walletService.recordExternalMovement(
                    event.getBuyerId(),
                    MovementType.P2P_PURCHASE,
                    event.getPrice(),
                    "Compra de " + event.getTokenCount() + " tokens del proyecto " + event.getProjectId(),
                    event.getOrderId(),
                    buyEventId
            );
        } catch (Exception e) {
            log.error("Error procesando evento marketplace.trade_settled para orden {}: {}",
                    event.getOrderId(), e.getMessage(), e);
        }
    }
}
