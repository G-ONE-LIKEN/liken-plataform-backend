package com.plataforma.projects.config;

import com.plataforma.projects.model.Project;
import com.plataforma.projects.model.ProjectMetric;
import com.plataforma.projects.model.ProjectState;
import com.plataforma.projects.model.EnergyType;
import com.plataforma.projects.repository.ProjectRepository;
import com.plataforma.projects.repository.ProjectMetricRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final ProjectRepository projectRepository;
    private final ProjectMetricRepository metricRepository;

    @Override
    public void run(String... args) {
        if (projectRepository.count() > 0) return;

        Project solar = projectRepository.save(Project.builder()
                .name("Campo Solar Mendoza")
                .description("Parque solar en el Valle de Uco con 5 MW de capacidad instalada.")
                .ownerId(1L)
                .state(ProjectState.OPEN)
                .energyType(EnergyType.SOLAR)
                .province("Mendoza")
                .country("Argentina")
                .latitude(new BigDecimal("-33.5986"))
                .longitude(new BigDecimal("-69.1566"))
                .installedCapacityMW(new BigDecimal("5.0"))
                .totalTokens(new BigDecimal("50000"))
                .earlyBirdPrice(new BigDecimal("8.0000"))
                .standardPrice(new BigDecimal("10.0000"))
                .minimumInvestment(new BigDecimal("100.00"))
                .expectedAnnualYield(new BigDecimal("8.50"))
                .expectedOpenDate(LocalDate.of(2026, 6, 1))
                .active(true)
                .build());

        projectRepository.save(Project.builder()
                .name("Parque Eolico Patagonia Sur")
                .description("Aerogeneradores en la Patagonia con vientos constantes de mas de 8 m/s promedio.")
                .ownerId(1L)
                .state(ProjectState.PRE_OPEN)
                .energyType(EnergyType.WIND)
                .province("Santa Cruz")
                .country("Argentina")
                .latitude(new BigDecimal("-51.6230"))
                .longitude(new BigDecimal("-69.2168"))
                .installedCapacityMW(new BigDecimal("12.0"))
                .totalTokens(new BigDecimal("120000"))
                .earlyBirdPrice(new BigDecimal("8.5000"))
                .standardPrice(new BigDecimal("12.0000"))
                .minimumInvestment(new BigDecimal("85.00"))
                .expectedAnnualYield(new BigDecimal("10.20"))
                .expectedOpenDate(LocalDate.of(2026, 12, 1))
                .active(true)
                .build());

        projectRepository.save(Project.builder()
                .name("Mini Hidro Neuquén")
                .description("Central hidroeléctrica de paso sobre el rio Limay.")
                .ownerId(2L)
                .state(ProjectState.DRAFT)
                .energyType(EnergyType.HYDRO)
                .province("Neuquén")
                .country("Argentina")
                .latitude(new BigDecimal("-38.9516"))
                .longitude(new BigDecimal("-68.0591"))
                .installedCapacityMW(new BigDecimal("2.5"))
                .totalTokens(new BigDecimal("25000"))
                .earlyBirdPrice(new BigDecimal("10.0000"))
                .standardPrice(new BigDecimal("12.0000"))
                .minimumInvestment(new BigDecimal("120.00"))
                .expectedAnnualYield(new BigDecimal("7.80"))
                .expectedOpenDate(LocalDate.of(2027, 3, 1))
                .active(true)
                .build());

        metricRepository.save(ProjectMetric.builder()
                .project(solar)
                .periodStart(LocalDate.of(2026, 1, 1))
                .periodEnd(LocalDate.of(2026, 1, 31))
                .energyGeneratedKwh(new BigDecimal("185000.00"))
                .revenueGenerated(new BigDecimal("74000.00"))
                .source("CAMMESA")
                .build());

        metricRepository.save(ProjectMetric.builder()
                .project(solar)
                .periodStart(LocalDate.of(2026, 2, 1))
                .periodEnd(LocalDate.of(2026, 2, 28))
                .energyGeneratedKwh(new BigDecimal("162000.00"))
                .revenueGenerated(new BigDecimal("64800.00"))
                .source("CAMMESA")
                .build());
    }
}
