package com.plataforma.oracle.dto;

import java.math.BigDecimal;

/**
 * Proyeccion de un proyecto activo recibida desde project-service
 * (GET /internal/projects/active). Misma forma que el DTO que expone
 * project-service — no se comparte la clase entre servicios, cada uno
 * define su propia copia de lo que consume/produce.
 */
public record ActiveProjectOracleDto(
        Long projectId,
        BigDecimal installedCapacityMW) {
}