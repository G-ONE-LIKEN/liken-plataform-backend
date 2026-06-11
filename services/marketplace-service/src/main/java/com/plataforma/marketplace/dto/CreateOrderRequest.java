package com.plataforma.marketplace.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Request para crear una orden de venta en el marketplace.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    @NotNull(message = "El projectId es obligatorio")
    private Long projectId;

    @NotNull(message = "La cantidad de tokens es obligatoria")
    @DecimalMin(value = "0.00000001", message = "La cantidad de tokens debe ser mayor a 0")
    private BigDecimal tokensAmount;

    @NotNull(message = "El precio por token es obligatorio")
    @DecimalMin(value = "0.01", message = "El precio por token debe ser al menos 0.01")
    private BigDecimal pricePerToken;
}
