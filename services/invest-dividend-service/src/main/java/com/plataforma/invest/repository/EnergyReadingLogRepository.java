package com.plataforma.invest.repository;

import com.plataforma.invest.model.EnergyReadingLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EnergyReadingLogRepository extends JpaRepository<EnergyReadingLog, Long> {
    boolean existsByEventId(String eventId);
}
