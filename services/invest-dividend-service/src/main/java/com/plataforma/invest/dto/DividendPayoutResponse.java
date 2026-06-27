package com.plataforma.invest.dto;

import com.plataforma.invest.model.DividendPayout;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record DividendPayoutResponse(
        Long id,
        String batchId,
        Long projectId,
        Long userId,
        String walletAddress,
        BigDecimal amount,
        String txHash,
        Long blockNumber,
        LocalDateTime paidAt
) {
    public static DividendPayoutResponse from(DividendPayout p) {
        return DividendPayoutResponse.builder()
                .id(p.getId())
                .batchId(p.getBatchId())
                .projectId(p.getProjectId())
                .userId(p.getUserId())
                .walletAddress(p.getWalletAddress())
                .amount(p.getAmount())
                .txHash(p.getTxHash())
                .blockNumber(p.getBlockNumber())
                .paidAt(p.getPaidAt())
                .build();
    }
}
