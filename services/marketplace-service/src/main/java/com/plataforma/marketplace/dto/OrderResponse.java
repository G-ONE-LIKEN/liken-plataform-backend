package com.plataforma.marketplace.dto;

import com.plataforma.marketplace.model.Order;
import com.plataforma.marketplace.model.OrderStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Projeccion publica de una orden del marketplace.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {

    private Long id;
    private Long sellerId;
    private Long projectId;
    private BigDecimal tokensAmount;
    private BigDecimal pricePerToken;
    private BigDecimal totalPrice;
    private OrderStatus status;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;

    public static OrderResponse from(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .sellerId(order.getSellerId())
                .projectId(order.getProjectId())
                .tokensAmount(order.getTokensAmount())
                .pricePerToken(order.getPricePerToken())
                .totalPrice(order.getTokensAmount().multiply(order.getPricePerToken()))
                .status(order.getStatus())
                .expiresAt(order.getExpiresAt())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
