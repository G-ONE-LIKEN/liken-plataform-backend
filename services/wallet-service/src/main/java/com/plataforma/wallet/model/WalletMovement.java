package com.plataforma.wallet.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "wallet_movements")
@Getter @Setter
@Builder @NoArgsConstructor @AllArgsConstructor
public class WalletMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MovementType type;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "balance_before", nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    private String description;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    /**
     * ID único del evento Kafka que originó este movimiento. Permite detectar
     * y descartar duplicados (at-least-once delivery — ver DD010).
     * NULL para movimientos creados por endpoints HTTP (deposit/withdraw).
     */
    @Column(name = "external_event_id", length = 64, unique = true)
    private String externalEventId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
