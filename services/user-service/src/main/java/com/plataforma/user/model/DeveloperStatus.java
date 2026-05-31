package com.plataforma.user.model;

/**
 * Estado de verificación del desarrollador de proyectos.
 *
 *   PENDING  → se registró como DEVELOPER, esperando revisión de SUPER_ADMIN/ADMIN
 *   APPROVED → verificado; puede dar de alta proyectos
 *   REJECTED → rechazado; no puede publicar proyectos
 *
 * Solo aplica a usuarios con rol DEVELOPER. El campo es null para otros roles.
 */
public enum DeveloperStatus {
    PENDING,
    APPROVED,
    REJECTED
}
