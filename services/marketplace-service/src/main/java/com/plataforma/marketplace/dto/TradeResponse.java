package com.plataforma.marketplace.dto;

import com.plataforma.marketplace.model.Trade;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Projección de una transacción completada del marketplace.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradeResponse {

    private Long id;
    private Long orderId;
    private Long sellerId;
    private Long buyerId;
    private Long projectId;
    private BigDecimal tokensAmount;
    private BigDecimal pricePerToken;
    private BigDecimal totalPrice;
    private BigDecimal feeAmount;
    private LocalDateTime createdAt;

    public static TradeResponse from(Trade trade) {
        return TradeResponse.builder()
                .id(trade.getId())
                .orderId(trade.getOrderId())
                .sellerId(trade.getSellerId())
                .buyerId(trade.getBuyerId())
                .projectId(trade.getProjectId())
                .tokensAmount(trade.getTokensAmount())
                .pricePerToken(trade.getPricePerToken())
                .totalPrice(trade.getTotalPrice())
                .feeAmount(trade.getFeeAmount())
                .createdAt(trade.getCreatedAt())
                .build();
    }
}
