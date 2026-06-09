package com.plataforma.marketplace.model;

/**
 * Lado de la orden: SELL (venta) o BUY (compra).
 * En MVP solo se usa SELL; BUY queda reservado para matching parcial post-MVP.
 */
public enum OrderSide {
    SELL,
    BUY
}
