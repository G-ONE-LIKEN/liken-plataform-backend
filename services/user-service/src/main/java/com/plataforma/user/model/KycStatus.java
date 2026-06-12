package com.plataforma.user.model;

/**
 * Estado del proceso KYC del usuario (ver DD013).
 *
 *   NOT_STARTED → el usuario aun no subio documentos
 *   PENDING     → documentos subidos, esperando revision de ADMIN
 *   APPROVED    → ADMIN aprobo; puede invertir y operar en marketplace
 *   REJECTED    → ADMIN rechazo; el usuario debe re-subir documentos
 *
 * Las operaciones que requieren KYC aprobado (invest-dividend.POST /api/investments,
 * marketplace.POST /api/orders) rechazan con 403 KYC_REQUIRED si el estado no es APPROVED.
 */
public enum KycStatus {
    NOT_STARTED,
    PENDING,
    APPROVED,
    REJECTED
}
