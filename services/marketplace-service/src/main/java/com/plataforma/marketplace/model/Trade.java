package com.plataforma.marketplace.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Transacción completada del marketplace: un comprador ejecutó una orden de venta.
 *
 * <p>Un {@code Trade} siempre tiene un {@link Order} asociado. El fee (si aplica)
 * se descuenta del lado del vendedor.
 */
@Entity
@Table(name = "trades")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "tokens_amount", nullable = false, precision = 20, scale = 8)
    private BigDecimal tokensAmount;

    @Column(name = "price_per_token", nullable = false, precision = 20, scale = 6)
    private BigDecimal pricePerToken;

    @Column(name = "total_price", nullable = false, precision = 20, scale = 6)
    private BigDecimal totalPrice;

    @Column(name = "fee_amount", nullable = false, precision = 20, scale = 6)
    @Builder.Default
    private BigDecimal feeAmount = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
