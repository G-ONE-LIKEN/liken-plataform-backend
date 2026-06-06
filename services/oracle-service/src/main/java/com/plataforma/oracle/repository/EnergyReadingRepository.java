package com.plataforma.oracle.repository;

import com.plataforma.oracle.model.EnergyReading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EnergyReadingRepository extends JpaRepository<EnergyReading, Long> {

    List<EnergyReading> findByProjectIdOrderByReadingTimestampDesc(Long projectId);

    boolean existsByProjectIdAndReadingTimestamp(Long projectId, LocalDateTime readingTimestamp);
}