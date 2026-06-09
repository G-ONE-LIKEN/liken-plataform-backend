package com.plataforma.marketplace.repository;

import com.plataforma.marketplace.model.Trade;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {

    /** Historial de transacciones donde el usuario fue comprador o vendedor. */
    Page<Trade> findBySellerIdOrBuyerIdOrderByCreatedAtDesc(
            Long sellerId, Long buyerId, Pageable pageable);
}
