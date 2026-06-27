package com.plataforma.invest.repository;

import com.plataforma.invest.model.ProjectEnergyAccumulator;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProjectEnergyAccumulatorRepository
        extends JpaRepository<ProjectEnergyAccumulator, Long> {

    /**
     * Asegura que exista una fila para el proyecto sin pisar valores. Postgres
     * {@code ON CONFLICT DO NOTHING}: si la fila ya esta, no toca nada; si no,
     * la inserta con saldos en cero. Defense in depth contra el race condition
     * cuando dos lecturas concurrentes intentan crear el acumulador del mismo
     * proyecto.
     */
    @Modifying
    @Query(value = """
            INSERT INTO project_energy_accumulator
                (project_id, pending_kwh, pending_usdc, in_flight_usdc)
            VALUES (:projectId, 0, 0, 0)
            ON CONFLICT (project_id) DO NOTHING
            """, nativeQuery = true)
    void ensureExists(@Param("projectId") Long projectId);

    /**
     * SELECT FOR UPDATE: serializa accrueReading() concurrentes sobre el mismo
     * proyecto. Sin este lock, dos consumers podrian ver ambos pending=0.5 a la
     * vez, ambos sumar y ambos cruzar el umbral, disparando deposits duplicados.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM ProjectEnergyAccumulator a WHERE a.projectId = :projectId")
    Optional<ProjectEnergyAccumulator> findByIdForUpdate(@Param("projectId") Long projectId);
}
