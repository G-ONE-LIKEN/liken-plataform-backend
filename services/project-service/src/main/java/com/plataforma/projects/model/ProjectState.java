package com.plataforma.projects.model;

public enum ProjectState {
    /** Proyecto creado por un developer, a la espera de aprobación de un administrador. */
    PENDING_APPROVAL,
    /** Proyecto aprobado por admin, aún no publicado. */
    DRAFT,
    /** En etapa de pre-apertura / captación de interés. */
    PRE_OPEN,
    /** Abierto a inversiones. */
    OPEN,
    /** Proyecto finalizado normalmente. */
    CLOSED,
    /** Proyecto cancelado (desde cualquier estado no-final). Penalizaciones/reembolsos a definir. */
    CANCELLED
}
