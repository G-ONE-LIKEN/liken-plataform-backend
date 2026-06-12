package com.plataforma.event.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Publicado por marketplace-service en el topico marketplace.order_matched.
 * wallet-service lo consume para registrar el movimiento de la transaccion P2P:
 * - acredita al vendedor (P2P_SALE)
 * - debita al comprador (P2P_PURCHASE)
 *
 * Campos canonicos (ver DD010): eventId, occurredAt, version.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OrderMatchedEvent {
    private String eventId;       // UUID v4 — base de la idempotencia (DD010)
    private String occurredAt;    // ISO 8601 UTC
    private int version;          // version del schema

    private Long sellerId;
    private Long buyerId;
    private Long projectId;
    private Integer tokenCount;
    private BigDecimal price;
    private String orderId;
}
