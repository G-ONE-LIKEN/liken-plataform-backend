package com.plataforma.event.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Publicado por invest-dividend-service en el tópico dividends.distributed.
 * wallet-service lo consume para acreditar el dividendo al inversor.
 *
 * Campos canónicos (ver DD010): eventId, occurredAt, version.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DividendDistributedEvent {
    private String eventId;       // UUID v4 — base de la idempotencia (DD010)
    private String occurredAt;    // ISO 8601 UTC
    private int version;          // versión del schema

    private Long userId;
    private Long projectId;
    private BigDecimal amount;
    private String dividendId;
}
