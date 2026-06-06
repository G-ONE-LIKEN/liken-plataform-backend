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

    /**
     * LKN asignados al tramo de venta primaria de este proyecto.
     * (No es un cap de mint — el supply de LKN es global y fijo desde el TGE.)
     */
    @NotNull(message = "El total de tokens es obligatorio")
    @DecimalMin(value = "1", message = "Debe haber al menos 1 token")
    private BigDecimal totalTokens;

    /** Precio en USDC por LKN durante la ronda (etapa FUNDING). */
    @NotNull(message = "El early bird price es obligatorio")
    @DecimalMin(value = "0.01", message = "El early bird price debe ser mayor a cero")
    private BigDecimal earlyBirdPrice;

    /** Precio en USDC por LKN post-ronda (etapa ACTIVE). Debe ser mayor que earlyBirdPrice. */
    @NotNull(message = "El standard price es obligatorio")
    @DecimalMin(value = "0.01", message = "El standard price debe ser mayor a cero")
    private BigDecimal standardPrice;

    @DecimalMin(value = "0.01", message = "La inversión mínima debe ser mayor a cero")
    private BigDecimal minimumInvestment;

    @DecimalMin(value = "0.01", message = "El soft cap debe ser mayor a cero")
    private BigDecimal softCap;

    @DecimalMin(value = "0.01", message = "El hard cap debe ser mayor a cero")
    private BigDecimal hardCap;

    /**
     * Fecha esperada de apertura del parque. Único campo de fecha. Se usa también como
     * {@code OfferingContract.deadline} al deployar el contrato (deadline implícito del soft cap).
     */
    private LocalDate expectedOpenDate;

    private BigDecimal expectedAnnualYield;

    @DecimalMin(value = "0.0001", message = "La producción anual estimada debe ser mayor a cero")
    private BigDecimal expectedAnnualProductionMWh;
}
