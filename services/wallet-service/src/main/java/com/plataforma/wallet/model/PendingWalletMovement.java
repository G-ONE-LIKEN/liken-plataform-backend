package com.plataforma.wallet.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Movimiento contable pendiente de reconciliacion.
 *
 * <p>Se guarda cuando un evento on-chain llega (compra, dividendo, refund)
 * con {@code walletAddress} resuelta pero sin {@code userId} y sin una
 * {@link Wallet} existente en el sistema. Cuando el usuario vincula la wallet,
 * el {@code WalletLinkedConsumer} mueve estos registros a {@link WalletMovement}
 * reales y recalcula el balance.
 */
@Entity
@Table(name = "pending_wallet_movements")
@Getter @Setter
@Builder @NoArgsConstructor @AllArgsConstructor
public class PendingWalletMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wallet_address", nullable = false, length = 42)
    private String walletAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MovementType type;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    private String description;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "external_event_id", length = 128, unique = true)
    private String externalEventId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
