package com.plataforma.user.kyc.dto;

import com.plataforma.user.model.KycStatus;
import lombok.Getter;
import lombok.Setter;

/**
 * Payload del endpoint PUT /api/users/{id}/kyc (ADMIN).
 * decision debe ser APPROVED o REJECTED. Si es REJECTED, rejectionReason es requerido.
 */
@Getter
@Setter
public class KycReviewRequest {
    private KycStatus decision;
    private String rejectionReason;
}
