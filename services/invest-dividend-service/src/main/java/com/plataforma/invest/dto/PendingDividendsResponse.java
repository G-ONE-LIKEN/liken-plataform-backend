package com.plataforma.invest.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PendingDividendsResponse {
    private String walletAddress;
    private BigDecimal pendingUsdc;
}
