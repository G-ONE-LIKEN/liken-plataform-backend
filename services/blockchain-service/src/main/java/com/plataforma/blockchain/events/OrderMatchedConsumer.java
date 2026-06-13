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

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMatchedConsumer {

    private final ObjectMapper objectMapper;
    private final TokenTransferService tokenTransferService;
    private final UserLookupClient userLookupClient;

    @KafkaListener(topics = "marketplace.order_matched", groupId = "blockchain-service")
    public void consume(String payload) {
        log.info("Recibido evento marketplace.order_matched: {}", payload);
        try {
            OrderMatchedEvent event = objectMapper.readValue(payload, OrderMatchedEvent.class);

            // 1. Obtener wallet del vendedor
            String sellerWallet = userLookupClient.userContext(event.getSellerId()).walletAddress();
            if (sellerWallet == null || sellerWallet.isBlank()) {
                log.error("Vendedor {} no tiene wallet vinculada. Ignorando evento.", event.getSellerId());
                return;
            }

            // 2. Obtener wallet del comprador
            String buyerWallet = userLookupClient.userContext(event.getBuyerId()).walletAddress();
            if (buyerWallet == null || buyerWallet.isBlank()) {
                log.error("Comprador {} no tiene wallet vinculada. Ignorando evento.", event.getBuyerId());
                return;
            }

            // 3. Ejecutar transferFrom on-chain
            log.info("Ejecutando transferFrom: seller={}, buyer={}, amount={}", sellerWallet, buyerWallet, event.getTokenCount());
            tokenTransferService.executeTransferFrom(sellerWallet, buyerWallet, new BigDecimal(event.getTokenCount()));

            log.info("Transferencia P2P ejecutada exitosamente en la blockchain para orden: {}", event.getOrderId());

        } catch (Exception e) {
            log.error("Error procesando evento marketplace.order_matched", e);
            // Si falla temporalmente, idealmente se debe reintentar o mandar a un DLQ.
            // Para el MVP logueamos el error.
        }
    }
}
