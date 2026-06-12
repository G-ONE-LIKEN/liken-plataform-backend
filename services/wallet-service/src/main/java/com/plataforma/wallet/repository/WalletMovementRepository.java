package com.plataforma.wallet.repository;

import com.plataforma.wallet.model.MovementType;
import com.plataforma.wallet.model.Wallet;
import com.plataforma.wallet.model.WalletMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface WalletMovementRepository extends JpaRepository<WalletMovement, Long> {

    Page<WalletMovement> findByWalletOrderByCreatedAtDesc(Wallet wallet, Pageable pageable);

    /**
     * Chequea si ya se proceso un evento Kafka con este eventId.
     * Base de la idempotencia de los consumers (ver DD010).
     */
    boolean existsByExternalEventId(String externalEventId);

    /**
     * Suma total agregada por tipo de movimiento dentro de un rango de fechas.
     * Devuelve [type, totalAmount, count]. util para reportes administrativos.
     */
    @Query("""
        SELECT m.type, COALESCE(SUM(m.amount), 0), COUNT(m)
        FROM WalletMovement m
        WHERE m.createdAt >= :from AND m.createdAt < :to
        GROUP BY m.type
        """)
    List<Object[]> sumAmountByTypeBetween(@Param("from") LocalDateTime from,
                                          @Param("to") LocalDateTime to);

    /**
     * Serie temporal mensual agregada por tipo: [year, month, type, totalAmount].
     * Permite construir graficos de evolucion por categoria.
     */
    @Query("""
        SELECT EXTRACT(YEAR FROM m.createdAt), EXTRACT(MONTH FROM m.createdAt), m.type, COALESCE(SUM(m.amount), 0)
        FROM WalletMovement m
        WHERE m.createdAt >= :from AND m.createdAt < :to
        GROUP BY EXTRACT(YEAR FROM m.createdAt), EXTRACT(MONTH FROM m.createdAt), m.type
        ORDER BY EXTRACT(YEAR FROM m.createdAt), EXTRACT(MONTH FROM m.createdAt)
        """)
    List<Object[]> monthlyAmountByType(@Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to);

    long countByType(MovementType type);
}
