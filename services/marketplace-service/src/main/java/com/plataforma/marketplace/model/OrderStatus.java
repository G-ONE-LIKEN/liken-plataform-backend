package com.plataforma.marketplace.model;

/**
 * Estado de una orden del marketplace.
 *
 * <ul>
 *   <li>{@link #OPEN} — visible, esperando un comprador.</li>
 *   <li>{@link #MATCHED} — completada (existe un {@link Trade} asociado).</li>
 *   <li>{@link #CANCELLED} — cancelada por el dueño o por cambio de estado del proyecto.</li>
 *   <li>{@link #EXPIRED} — vencio el TTL sin ser ejecutada.</li>
 * </ul>
 */
public enum OrderStatus {
    OPEN,
    PENDING_SETTLEMENT,
    MATCHED,
    CANCELLED,
    EXPIRED
}
