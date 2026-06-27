package com.plataforma.invest.repository;

import com.plataforma.invest.model.DividendBatch;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DividendBatchRepository extends JpaRepository<DividendBatch, String> {

    /**
     * SELECT FOR UPDATE para serializar los incrementos de confirmed/failed
     * cuando varios payouts del mismo batch confirman al mismo tiempo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM DividendBatch b WHERE b.batchId = :batchId")
    Optional<DividendBatch> findByIdForUpdate(@Param("batchId") String batchId);
}
