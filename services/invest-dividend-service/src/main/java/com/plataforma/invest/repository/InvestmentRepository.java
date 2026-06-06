package com.plataforma.invest.repository;

import com.plataforma.invest.model.Investment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface InvestmentRepository extends JpaRepository<Investment, Long> {
    Page<Investment> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Page<Investment> findByProjectIdOrderByCreatedAtDesc(Long projectId, Pageable pageable);
    List<Investment> findByUserIdIsNullAndWalletAddressIgnoreCase(String walletAddress);

    @Query("SELECT coalesce(sum(i.usdcAmount), 0) FROM Investment i WHERE i.userId = :userId")
    BigDecimal sumUsdcAmountByUserId(@Param("userId") Long userId);

    @Query("""
        SELECT COALESCE(SUM(i.usdcAmount), 0), COUNT(i)
        FROM Investment i
        WHERE i.createdAt >= :from AND i.createdAt < :to
        """)
    Object[] sumVolumeBetween(@Param("from") LocalDateTime from,
                              @Param("to") LocalDateTime to);

    @Query("""
        SELECT EXTRACT(YEAR FROM i.createdAt), EXTRACT(MONTH FROM i.createdAt), COALESCE(SUM(i.usdcAmount), 0), COUNT(i)
        FROM Investment i
        WHERE i.createdAt >= :from AND i.createdAt < :to
        GROUP BY EXTRACT(YEAR FROM i.createdAt), EXTRACT(MONTH FROM i.createdAt)
        ORDER BY EXTRACT(YEAR FROM i.createdAt), EXTRACT(MONTH FROM i.createdAt)
        """)
    List<Object[]> monthlyVolumeBetween(@Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to);
}
