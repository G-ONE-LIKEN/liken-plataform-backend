package com.plataforma.projects.dto.internal;

import java.math.BigDecimal;

public record ActiveProjectOracleDto(
        Long projectId,
        BigDecimal installedCapacityMW) {
}
