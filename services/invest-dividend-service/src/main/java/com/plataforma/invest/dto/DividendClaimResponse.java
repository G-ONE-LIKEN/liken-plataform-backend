package com.plataforma.invest.dto;

import com.plataforma.invest.model.DividendClaim;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class DividendClaimResponse {
    private Long id;
    private Long userId;
    private String walletAddress;
    private BigDecimal amount;
    private String txHash;
    private Long blockNumber;
    private LocalDateTime createdAt;

    public static DividendClaimResponse from(DividendClaim c) {
        return DividendClaimResponse.builder()
                .id(c.getId())
                .userId(c.getUserId())
                .walletAddress(c.getWalletAddress())
                .amount(c.getAmount())
                .txHash(c.getTxHash())
                .blockNumber(c.getBlockNumber())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
