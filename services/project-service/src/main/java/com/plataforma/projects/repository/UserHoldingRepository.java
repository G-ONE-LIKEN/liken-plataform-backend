package com.plataforma.projects.repository;

import com.plataforma.projects.model.UserHolding;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserHoldingRepository extends JpaRepository<UserHolding, Long> {

    Page<UserHolding> findByProjectId(Long projectId, Pageable pageable);

    Optional<UserHolding> findByUserIdAndProjectId(Long userId, Long projectId);

    Optional<UserHolding> findByWalletAddressIgnoreCaseAndProjectId(String walletAddress, Long projectId);

    List<UserHolding> findByUserIdIsNullAndWalletAddressIgnoreCase(String walletAddress);

    @Query("select coalesce(sum(h.tokensAmount), 0) from UserHolding h where h.project.id = :projectId")
    BigDecimal sumTokensAmountByProjectId(@Param("projectId") Long projectId);
}
