package com.plataforma.projects.dto;

import com.plataforma.projects.model.EnergyType;
import com.plataforma.projects.model.OnChainStatus;
import com.plataforma.projects.model.Project;
import com.plataforma.projects.model.ProjectState;
import com.plataforma.projects.model.RoundState;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class ProjectResponse {
    private Long id;
    private String name;
    private String description;
    private Long ownerId;
    private ProjectState state;
    private EnergyType energyType;
    private String province;
    private String country;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal installedCapacityMW;
    private BigDecimal totalTokens;

    /** Precio etapa FUNDING. */
    private BigDecimal earlyBirdPrice;
    /** Precio etapa ACTIVE. */
    private BigDecimal standardPrice;
    /** Precio vigente segun el estado actual ({@link Project#currentPrice()}). */
    private BigDecimal currentPrice;

    private BigDecimal minimumInvestment;
    private BigDecimal softCap;
    private BigDecimal hardCap;
    /** Apertura esperada del parque (también deadline del soft cap on-chain). */
    private LocalDate expectedOpenDate;
    private BigDecimal raisedAmount;
    private BigDecimal expectedAnnualYield;
    private BigDecimal expectedAnnualProductionMWh;
    private BigDecimal totalTokensSold;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private String rejectionReason;

    // ── On-chain ──────────────────────────────────────────────────────────────
    private Long registryProjectId;
    private String offeringContractAddress;
    private String deployTxHash;
    private Long deployBlockNumber;
    private OnChainStatus onChainStatus;
    private RoundState roundState;

    public static ProjectResponse from(Project p) {
        return ProjectResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .ownerId(p.getOwnerId())
                .state(p.getState())
                .energyType(p.getEnergyType())
                .province(p.getProvince())
                .country(p.getCountry())
                .latitude(p.getLatitude())
                .longitude(p.getLongitude())
                .installedCapacityMW(p.getInstalledCapacityMW())
                .totalTokens(p.getTotalTokens())
                .earlyBirdPrice(p.getEarlyBirdPrice())
                .standardPrice(p.getStandardPrice())
                .currentPrice(p.currentPrice())
                .minimumInvestment(p.getMinimumInvestment())
                .softCap(p.getSoftCap())
                .hardCap(p.getHardCap())
                .expectedOpenDate(p.getExpectedOpenDate())
                .raisedAmount(p.getRaisedAmount())
                .expectedAnnualYield(p.getExpectedAnnualYield())
                .expectedAnnualProductionMWh(p.getExpectedAnnualProductionMWh())
                .totalTokensSold(p.getTotalTokensSold())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .approvedBy(p.getApprovedBy())
                .approvedAt(p.getApprovedAt())
                .rejectionReason(p.getRejectionReason())
                .registryProjectId(p.getRegistryProjectId())
                .offeringContractAddress(p.getOfferingContractAddress())
                .deployTxHash(p.getDeployTxHash())
                .deployBlockNumber(p.getDeployBlockNumber())
                .onChainStatus(p.getOnChainStatus())
                .roundState(p.getRoundState())
                .build();
    }
}
