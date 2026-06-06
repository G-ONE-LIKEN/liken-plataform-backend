package com.plataforma.invest.repository;

import com.plataforma.invest.model.DividendClaim;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DividendClaimRepository extends JpaRepository<DividendClaim, Long> {
    Page<DividendClaim> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    List<DividendClaim> findByUserIdIsNullAndWalletAddressIgnoreCase(String walletAddress);
}
