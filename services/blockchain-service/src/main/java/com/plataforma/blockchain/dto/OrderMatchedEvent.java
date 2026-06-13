package com.plataforma.blockchain.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OrderMatchedEvent {
    private String eventId;
    private String occurredAt;
    private int version;

    private Long sellerId;
    private Long buyerId;
    private Long projectId;
    private Integer tokenCount;
    private BigDecimal price;
    private String orderId;
}
