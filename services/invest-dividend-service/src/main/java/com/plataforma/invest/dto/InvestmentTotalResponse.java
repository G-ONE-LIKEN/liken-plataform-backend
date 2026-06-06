package com.plataforma.invest.dto;

import com.plataforma.invest.model.UserInvestmentTotal;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class InvestmentTotalResponse {
    private Long userId;
    private BigDecimal totalUsdcInvested;
    private String currentTier;

    public static InvestmentTotalResponse from(UserInvestmentTotal t) {
        return InvestmentTotalResponse.builder()
                .userId(t.getUserId())
                .totalUsdcInvested(t.getTotalUsdcInvested())
                .currentTier(t.getCurrentTier())
                .build();
    }
}
