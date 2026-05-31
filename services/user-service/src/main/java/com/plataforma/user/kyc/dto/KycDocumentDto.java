package com.plataforma.user.kyc.dto;

import com.plataforma.user.kyc.model.KycDocumentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class KycDocumentDto {
    private Long id;
    private String documentType;
    private KycDocumentStatus status;
    private LocalDateTime uploadedAt;
    private LocalDateTime reviewedAt;
    private String rejectionReason;
}
