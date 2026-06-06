package com.plataforma.invest.repository;

import com.plataforma.invest.model.Investment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InvestmentRepository extends JpaRepository<Investment, Long> {
    Page<Investment> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Page<Investment> findByProjectIdOrderByCreatedAtDesc(Long projectId, Pageable pageable);
}
