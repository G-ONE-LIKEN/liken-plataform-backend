package com.plataforma.invest.repository;

import com.plataforma.invest.model.DividendPayout;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DividendPayoutRepository extends JpaRepository<DividendPayout, Long> {

    boolean existsByPayoutEventId(String payoutEventId);

    Page<DividendPayout> findByUserIdOrderByPaidAtDesc(Long userId, Pageable pageable);

    Page<DividendPayout> findByWalletAddressIgnoreCaseOrderByPaidAtDesc(String walletAddress, Pageable pageable);

    Page<DividendPayout> findByProjectIdOrderByPaidAtDesc(Long projectId, Pageable pageable);
}
