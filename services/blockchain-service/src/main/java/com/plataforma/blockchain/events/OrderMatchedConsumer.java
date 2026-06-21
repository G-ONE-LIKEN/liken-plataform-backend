package com.plataforma.blockchain.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataforma.blockchain.dto.OrderMatchedEvent;
import com.plataforma.blockchain.service.TokenTransferService;
import com.plataforma.blockchain.service.UserLookupClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import org.springframework.kafka.core.KafkaTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMatchedConsumer {

    private final ObjectMapper objectMapper;
    private final TokenTransferService tokenTransferService;
    private final UserLookupClient userLookupClient;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = "marketplace.order_matched", groupId = "blockchain-service")
    public void consume(String payload) {
        log.info("Recibido evento marketplace.order_matched: {}", payload);
        OrderMatchedEvent event = null;
        try {
            event = objectMapper.readValue(payload, OrderMatchedEvent.class);

            // 1. Obtener wallet del vendedor
            String sellerWallet = userLookupClient.userContext(event.getSellerId()).walletAddress();
            if (sellerWallet == null || sellerWallet.isBlank()) {
                log.error("Vendedor {} no tiene wallet vinculada. Ignorando evento.", event.getSellerId());
                publishFailure(event.getOrderId(), "Seller wallet not linked");
                return;
            }

            // 2. Obtener wallet del comprador
            String buyerWallet = userLookupClient.userContext(event.getBuyerId()).walletAddress();
            if (buyerWallet == null || buyerWallet.isBlank()) {
                log.error("Comprador {} no tiene wallet vinculada. Ignorando evento.", event.getBuyerId());
                publishFailure(event.getOrderId(), "Buyer wallet not linked");
                return;
            }

            // 3. Ejecutar settleTrade on-chain
            log.info("Ejecutando settleTrade: orderId={}, seller={}, buyer={}, amount={}, totalUsdc={}", 
                    event.getOrderId(), sellerWallet, buyerWallet, event.getTokenCount(), event.getPrice());
            
            BigInteger orderId = new BigInteger(event.getOrderId());
            BigInteger feePercent = BigInteger.valueOf(100); // 1.0%

            tokenTransferService.executeSettleTrade(
                    orderId,
                    sellerWallet,
                    buyerWallet,
                    new BigDecimal(event.getTokenCount()),
                    event.getPrice(),
                    feePercent
            );

            log.info("Liquidación P2P enviada exitosamente a la blockchain para orden: {}", event.getOrderId());

        } catch (Exception e) {
            log.error("Error procesando evento marketplace.order_matched", e);
            if (event != null && event.getOrderId() != null) {
                publishFailure(event.getOrderId(), e.getMessage());
            }
        }
    }

    private void publishFailure(String orderId, String reason) {
        try {
            java.util.Map<String, Object> failurePayload = new java.util.HashMap<>();
            failurePayload.put("orderId", orderId);
            failurePayload.put("reason", reason);
            kafkaTemplate.send("blockchain.trade_failed", orderId, failurePayload);
            log.info("Publicado evento blockchain.trade_failed para orderId={}", orderId);
        } catch (Exception ex) {
            log.error("No se pudo publicar blockchain.trade_failed para orderId={}", orderId, ex);
        }
    }
}
