package com.plataforma.wallet.repository;

import com.plataforma.wallet.model.PendingWalletMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PendingWalletMovementRepository extends JpaRepository<PendingWalletMovement, Long> {
    @Query("SELECT p FROM PendingWalletMovement p WHERE lower(p.walletAddress) = lower(:walletAddress)")
    List<PendingWalletMovement> findByWalletAddress(@Param("walletAddress") String walletAddress);
    boolean existsByExternalEventId(String externalEventId);
}
