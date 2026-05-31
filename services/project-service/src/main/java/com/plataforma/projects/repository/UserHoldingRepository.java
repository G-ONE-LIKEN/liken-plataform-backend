package com.plataforma.projects.repository;

import com.plataforma.projects.model.UserHolding;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserHoldingRepository extends JpaRepository<UserHolding, Long> {

    Page<UserHolding> findByProjectId(Long projectId, Pageable pageable);

    Optional<UserHolding> findByUserIdAndProjectId(Long userId, Long projectId);
}
