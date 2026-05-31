package com.plataforma.user.kyc.model;

/**
 * Estado de un documento KYC individual subido por un usuario.
 * Distinto de {@link com.plataforma.user.model.KycStatus} — ese es el estado
 * agregado del usuario (basado en los documentos).
 */
public enum KycDocumentStatus {
    PENDING,
    APPROVED,
    REJECTED
}
