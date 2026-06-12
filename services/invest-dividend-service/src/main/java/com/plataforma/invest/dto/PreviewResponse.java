package com.plataforma.invest.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Respuesta del endpoint de preview: dada una intencion de invertir
 * {@code usdcAmount} en {@code projectId}, devuelve cuantos LKN recibiria el
 * inversor al precio vigente y si el proyecto acepta inversiones.
 */
@Data
@Builder
public class PreviewResponse {
    private Long projectId;
    private String state;
    private BigDecimal currentPrice;
    private BigDecimal usdcAmount;
    private BigDecimal lknAmount;
    private String offeringContractAddress;
    private boolean canInvest;
    private String reason;
}
