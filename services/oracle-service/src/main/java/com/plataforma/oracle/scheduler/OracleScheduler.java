package com.plataforma.oracle.scheduler;

import com.plataforma.oracle.client.ProjectServiceClient;
import com.plataforma.oracle.dto.ActiveProjectOracleDto;
import com.plataforma.oracle.event.OracleEventPublisher;
import com.plataforma.oracle.model.EnergyReading;
import com.plataforma.oracle.repository.EnergyReadingRepository;
import com.plataforma.oracle.simulator.SolarCurveSimulator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OracleScheduler {

    private final ProjectServiceClient projectServiceClient;
    private final SolarCurveSimulator solarCurveSimulator;
    private final EnergyReadingRepository energyReadingRepository;
    private final OracleEventPublisher oracleEventPublisher;

    @Value("${oracle.simulation.interval-ms}")
    private long intervalMs;

    @Scheduled(fixedRateString = "${oracle.simulation.interval-ms}")
    public void runSimulationCycle() {
        List<ActiveProjectOracleDto> activeProjects = projectServiceClient.listActiveProjects();

        if (activeProjects.isEmpty()) {
            log.info("Ciclo del oracle: no hay proyectos activos para simular");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        log.info("Ciclo del oracle iniciado: {} proyectos activos", activeProjects.size());

        for (ActiveProjectOracleDto project : activeProjects) {
            try {
                processProject(project, now);
            } catch (Exception ex) {
                log.error("Error simulando lectura para projectId={}", project.projectId(), ex);
            }
        }
    }

    private void processProject(ActiveProjectOracleDto project, LocalDateTime momento) {
        Long projectId = project.projectId();
        BigDecimal installedCapacityMW = project.installedCapacityMW();

        if (installedCapacityMW == null) {
            log.warn("ProjectId={} no tiene installedCapacityMW, se omite", projectId);
            return;
        }

        if (energyReadingRepository.existsByProjectIdAndReadingTimestamp(projectId, momento)) {
            log.debug("Ya existe una lectura para projectId={} en timestamp={}, se omite", projectId, momento);
            return;
        }

        int intervalMinutes = (int) (intervalMs / 60000);
        BigDecimal energyKWh = solarCurveSimulator.simulateEnergyKWh(installedCapacityMW, momento, intervalMinutes);

        EnergyReading reading = EnergyReading.builder()
                .projectId(projectId)
                .readingTimestamp(momento)
                .energyKWh(energyKWh)
                .build();

        EnergyReading saved = energyReadingRepository.save(reading);
        oracleEventPublisher.publishEnergyReading(saved);
    }
}