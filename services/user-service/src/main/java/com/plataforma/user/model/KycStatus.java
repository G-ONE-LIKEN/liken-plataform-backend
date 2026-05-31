package com.plataforma.user.model;

/**
 * Estado del proceso KYC del usuario (ver DD013).
 *
 *   NOT_STARTED → el usuario aún no subió documentos
 *   PENDING     → documentos subidos, esperando revisión de ADMIN
 *   APPROVED    → ADMIN aprobó; puede invertir y operar en marketplace
 *   REJECTED    → ADMIN rechazó; el usuario debe re-subir documentos
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
