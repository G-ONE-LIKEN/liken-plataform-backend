package com.plataforma.wallet.repository;

import com.plataforma.wallet.model.Wallet;
import com.plataforma.wallet.model.WalletMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletMovementRepository extends JpaRepository<WalletMovement, Long> {

    Page<WalletMovement> findByWalletOrderByCreatedAtDesc(Wallet wallet, Pageable pageable);

    /**
     * Chequea si ya se procesó un evento Kafka con este eventId.
     * Base de la idempotencia de los consumers (ver DD010).
     */
    boolean existsByExternalEventId(String externalEventId);
}
