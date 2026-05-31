package com.plataforma.event.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Publicado por invest-dividend-service en el tópico investment.token_purchased.
 * wallet-service lo consume para debitar el costo de la compra.
 *
 * Campos canónicos (ver DD010): eventId, occurredAt, version.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TokenPurchasedEvent {
    private String eventId;       // UUID v4 — base de la idempotencia (DD010)
    private String occurredAt;    // ISO 8601 UTC — cuándo ocurrió el hecho
    private int version;          // versión del schema, empieza en 1

    private Long userId;
    private Long projectId;
    private Integer tokenCount;
    private BigDecimal totalPrice;
    private String transactionId;
}
