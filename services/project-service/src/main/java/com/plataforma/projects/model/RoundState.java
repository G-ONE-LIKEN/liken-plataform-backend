package com.plataforma.projects.model;

/**
 * Estado de la ronda primaria on-chain. Refleja {@code OfferingContract.state}.
 *
 * <p>Es ortogonal al {@link ProjectState} (que modela el ciclo de vida del proyecto
 * a nivel negocio). Mientras el ProjectState dice "este proyecto esta abierto a
 * inversiones", el RoundState dice "la ronda de venta primaria esta abierta,
 * cerrada exitosamente o fallo".
 *
 * <p>Solo aplica desde que el proyecto pasa a {@link ProjectState#OPEN} (cuando el
 * OfferingContract esta desplegado). Antes de eso es {@code null}.
 *
 * <p>Mapeo con la chain:
 * <ul>
 *   <li>{@link #PENDING}: contrato desplegado, escrow depositado, ronda aun no abierta.</li>
 *   <li>{@link #OPEN}: ronda activa, los inversores pueden llamar {@code buy()}.</li>
 *   <li>{@link #FINALIZED}: ronda exitosa (soft cap superado), proyecto pasa a ACTIVE
 *       en el Registry. El backend lo refleja como {@link ProjectState#CLOSED}.</li>
 *   <li>{@link #FAILED}: deadline paso sin alcanzar soft cap; inversores pueden hacer refund.
 *       El backend lo refleja como {@link ProjectState#CANCELLED}.</li>
 * </ul>
 */
public enum RoundState {
    PENDING,
    OPEN,
    FINALIZED,
    FAILED
}
