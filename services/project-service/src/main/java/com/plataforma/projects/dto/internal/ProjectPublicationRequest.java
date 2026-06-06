package com.plataforma.projects.dto.internal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectPublicationRequest {
    private Long projectId;
    private Long ownerId;
    private String projectName;
    private String projectDescription;
    private BigDecimal earlyBirdPrice;
    private BigDecimal standardPrice;
    private BigDecimal totalTokens;
    private BigDecimal softCap;
    private BigDecimal hardCap;
    private LocalDate expectedOpenDate;
}
