package com.plataforma.projects.dto;

import com.plataforma.projects.model.EnergyType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ProjectRequest {
    @NotBlank(message = "El nombre es obligatorio")
    private String name;

    @NotBlank(message = "La descripción es obligatoria")
    private String description;

    @NotNull(message = "El tipo de energía es obligatorio")
    private EnergyType energyType;

    private String province;
    private String country;
    private BigDecimal latitude;
    private BigDecimal longitude;

    @DecimalMin(value = "0.0001", message = "La capacidad instalada debe ser mayor a cero")
    private BigDecimal installedCapacityMW;

    @NotNull(message = "El total de tokens es obligatorio")
    @DecimalMin(value = "1", message = "Debe haber al menos 1 token")
    private BigDecimal totalTokens;

    @NotNull(message = "El precio del token es obligatorio")
    @DecimalMin(value = "0.01", message = "El precio debe ser mayor a cero")
    private BigDecimal tokenPrice;

    @DecimalMin(value = "0.01", message = "La inversión mínima debe ser mayor a cero")
    private BigDecimal minimumInvestment;

    @DecimalMin(value = "0.01", message = "El soft cap debe ser mayor a cero")
    private BigDecimal softCap;

    @DecimalMin(value = "0.01", message = "El hard cap debe ser mayor a cero")
    private BigDecimal hardCap;

    private LocalDate softCapDeadline;
    private LocalDate expectedOpenDate;

    private BigDecimal expectedAnnualYield;

    @DecimalMin(value = "0.0001", message = "La producción anual estimada debe ser mayor a cero")
    private BigDecimal expectedAnnualProductionMWh;

    private LocalDate startDate;
    private LocalDate endDate;
}
