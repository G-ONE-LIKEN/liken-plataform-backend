package com.plataforma.invest.dto;

import com.plataforma.invest.model.Investment;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class InvestmentResponse {
    private Long id;
    private Long userId;
    private String walletAddress;
    private Long projectId;
    private String offeringContractAddress;
    private BigDecimal usdcAmount;
    private BigDecimal lknAmount;
    private String txHash;
    private Long blockNumber;
    private LocalDateTime createdAt;

    public static InvestmentResponse from(Investment i) {
        return InvestmentResponse.builder()
                .id(i.getId())
                .userId(i.getUserId())
                .walletAddress(i.getWalletAddress())
                .projectId(i.getProjectId())
                .offeringContractAddress(i.getOfferingContractAddress())
                .usdcAmount(i.getUsdcAmount())
                .lknAmount(i.getLknAmount())
                .txHash(i.getTxHash())
                .blockNumber(i.getBlockNumber())
                .createdAt(i.getCreatedAt())
                .build();
    }
}
