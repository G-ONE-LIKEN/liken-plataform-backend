package com.plataforma.marketplace.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Orden de venta (o compra futura) del marketplace P2P.
 *
 * <p>El vendedor publica una orden con la cantidad de tokens y el precio por token.
 * La orden permanece {@link OrderStatus#OPEN} hasta que un comprador la ejecuta,
 * el vendedor la cancela, o vence el TTL.
 *
 * <p>El matching es completo (sin partial fills en MVP): toda la orden se ejecuta
 * de una vez o permanece abierta.
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 4)
    @Builder.Default
    private OrderSide side = OrderSide.SELL;

    @Column(name = "tokens_amount", nullable = false, precision = 20, scale = 8)
    private BigDecimal tokensAmount;

    @Column(name = "price_per_token", nullable = false, precision = 20, scale = 6)
    private BigDecimal pricePerToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    @Builder.Default
    private OrderStatus status = OrderStatus.OPEN;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
