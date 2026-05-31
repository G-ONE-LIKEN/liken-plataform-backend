package com.plataforma.wallet.dto;

import com.plataforma.wallet.model.MovementType;
import com.plataforma.wallet.model.WalletMovement;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class MovementResponse {
    private Long id;
    private MovementType type;
    private BigDecimal amount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private String description;
    private String referenceId;
    private LocalDateTime createdAt;

    public static MovementResponse from(WalletMovement m) {
        return MovementResponse.builder()
                .id(m.getId())
                .type(m.getType())
                .amount(m.getAmount())
                .balanceBefore(m.getBalanceBefore())
                .balanceAfter(m.getBalanceAfter())
                .description(m.getDescription())
                .referenceId(m.getReferenceId())
                .createdAt(m.getCreatedAt())
                .build();
    }
}
