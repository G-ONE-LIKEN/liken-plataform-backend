package com.plataforma.invest.repository;

import com.plataforma.invest.model.Investment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface InvestmentRepository extends JpaRepository<Investment, Long> {
    Page<Investment> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Page<Investment> findByProjectIdOrderByCreatedAtDesc(Long projectId, Pageable pageable);
    List<Investment> findByUserIdIsNullAndWalletAddressIgnoreCase(String walletAddress);

    @Query("SELECT coalesce(sum(i.usdcAmount), 0) FROM Investment i WHERE i.userId = :userId")
    BigDecimal sumUsdcAmountByUserId(@Param("userId") Long userId);
}
