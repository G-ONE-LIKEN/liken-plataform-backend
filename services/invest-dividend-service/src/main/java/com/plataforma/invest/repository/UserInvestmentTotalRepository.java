package com.plataforma.invest.repository;

import com.plataforma.invest.model.UserInvestmentTotal;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserInvestmentTotalRepository extends JpaRepository<UserInvestmentTotal, Long> {

    /**
     * Bloqueo pesimista para sumar montos sin race conditions cuando llegan
     * varios eventos del mismo usuario casi simultáneamente.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserInvestmentTotal u WHERE u.userId = :userId")
    Optional<UserInvestmentTotal> findByUserIdForUpdate(@Param("userId") Long userId);
}
