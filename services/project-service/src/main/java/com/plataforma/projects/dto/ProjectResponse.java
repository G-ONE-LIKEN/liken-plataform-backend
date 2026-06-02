package com.plataforma.projects.dto;

import com.plataforma.projects.model.EnergyType;
import com.plataforma.projects.model.Project;
import com.plataforma.projects.model.ProjectState;
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
    private BigDecimal tokenPrice;
    private BigDecimal minimumInvestment;
    private BigDecimal softCap;
    private BigDecimal hardCap;
    private LocalDate softCapDeadline;
    private LocalDate expectedOpenDate;
    private BigDecimal raisedAmount;
    private BigDecimal expectedAnnualYield;
    private BigDecimal expectedAnnualProductionMWh;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private String rejectionReason;

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
                .tokenPrice(p.getTokenPrice())
                .minimumInvestment(p.getMinimumInvestment())
                .softCap(p.getSoftCap())
                .hardCap(p.getHardCap())
                .softCapDeadline(p.getSoftCapDeadline())
                .expectedOpenDate(p.getExpectedOpenDate())
                .raisedAmount(p.getRaisedAmount())
                .expectedAnnualYield(p.getExpectedAnnualYield())
                .expectedAnnualProductionMWh(p.getExpectedAnnualProductionMWh())
                .startDate(p.getStartDate())
                .endDate(p.getEndDate())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .approvedBy(p.getApprovedBy())
                .approvedAt(p.getApprovedAt())
                .rejectionReason(p.getRejectionReason())
                .build();
    }
}
