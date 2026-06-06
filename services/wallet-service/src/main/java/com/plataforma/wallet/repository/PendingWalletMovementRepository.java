package com.plataforma.wallet.repository;

import com.plataforma.wallet.model.PendingWalletMovement;
import com.plataforma.wallet.model.MovementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PendingWalletMovementRepository extends JpaRepository<PendingWalletMovement, Long> {
    @Query("SELECT p FROM PendingWalletMovement p WHERE lower(p.walletAddress) = lower(:walletAddress)")
    List<PendingWalletMovement> findByWalletAddress(@Param("walletAddress") String walletAddress);
    boolean existsByExternalEventId(String externalEventId);

    @Query("""
        SELECT p.type, COALESCE(SUM(p.amount), 0), COUNT(p)
        FROM PendingWalletMovement p
        WHERE p.createdAt >= :from AND p.createdAt < :to
        GROUP BY p.type
        """)
    List<Object[]> sumAmountByTypeBetween(@Param("from") LocalDateTime from,
                                          @Param("to") LocalDateTime to);

    @Query("""
        SELECT EXTRACT(YEAR FROM p.createdAt), EXTRACT(MONTH FROM p.createdAt), p.type, COALESCE(SUM(p.amount), 0)
        FROM PendingWalletMovement p
        WHERE p.createdAt >= :from AND p.createdAt < :to
        GROUP BY EXTRACT(YEAR FROM p.createdAt), EXTRACT(MONTH FROM p.createdAt), p.type
        ORDER BY EXTRACT(YEAR FROM p.createdAt), EXTRACT(MONTH FROM p.createdAt)
        """)
    List<Object[]> monthlyAmountByType(@Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to);
}
