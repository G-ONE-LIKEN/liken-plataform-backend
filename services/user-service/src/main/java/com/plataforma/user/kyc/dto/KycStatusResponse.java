package com.plataforma.user.kyc.dto;

import com.plataforma.user.model.KycStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Respuesta del estado KYC de un usuario — incluye el estado agregado y la lista
 * de documentos subidos (con su estado individual).
 */
@Getter
@Builder
@AllArgsConstructor
public class KycStatusResponse {
    private KycStatus status;
    private List<KycDocumentDto> documents;
}
