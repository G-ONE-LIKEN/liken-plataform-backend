package com.plataforma.projects.model;

/**
 * Estado del ciclo de vida del proyecto en el backend.
 *
 * <p>Es ortogonal al {@link RoundState}, que modela el estado on-chain de la
 * ronda primaria. ProjectState habla del proyecto como entidad de negocio;
 * RoundState habla del contrato {@code OfferingContract} que vende los LKN.
 *
 * <h3>Mapeo con la chain (ProjectRegistry.Stage) — Fase 6</h3>
 * <pre>
 *   PENDING_APPROVAL │ ─ pre-chain — admin todavia no aprobo la propuesta
 *   DRAFT            │ ─ pre-chain — aprobado pero oculto al inversor
 *   ─────────────────┴────────────────────────────────────────────────────
 *   PRE_OPEN         ↔ Registry FUNDING (ronda abierta, precio earlyBird)
 *   OPEN             ↔ Registry ACTIVE  (parque operativo, precio standard, dividendos)
 *   CLOSED           ↔ Registry PAUSED  (proyecto dado de baja por admin/owner)
 *   CANCELLED                            (ronda fallo o admin cancelo pre-PRE_OPEN)
 * </pre>
 *
 * <h3>Quién dispara cada transicion</h3>
 * <pre>
 *   PENDING_APPROVAL → DRAFT          admin aprueba (endpoint manual)
 *   DRAFT            → PRE_OPEN       admin/owner publica (deploya OfferingContract)
 *   PRE_OPEN         → OPEN           automatica on-chain: RoundFinalized
 *                    → CANCELLED      automatica on-chain: RoundFailed
 *   OPEN             → CLOSED         admin/owner da de baja (endpoint manual)
 *   * (no-final)     → CANCELLED      manual; desde OPEN solo admin
 * </pre>
 */
public enum ProjectState {
    /** Proyecto creado por un developer, a la espera de aprobacion de un administrador. */
    PENDING_APPROVAL,
    /**
     * Aprobado por admin, pero NO visible al inversor en el catalogo publico.
     * El owner aun no lo publico / aun no se desplego el OfferingContract.
     */
    DRAFT,
    /**
     * Ronda primaria abierta: pre-compra a precio {@code earlyBirdPrice}.
     *
     * <p>On-chain: el {@code ProjectRegistry} tiene este proyecto en {@code FUNDING}
     * y el {@code OfferingContract} acepta {@code buy()}. La transicion de salida
     * (a OPEN o a CANCELLED) la dispara el Blockchain Service a partir de
     * {@code RoundFinalized} / {@code RoundFailed}.
     */
    PRE_OPEN,
    /**
     * Parque operativo: la ronda primaria fue exitosa, el proyecto esta generando
     * y distribuyendo dividendos.
     *
     * <p>On-chain: {@code ProjectRegistry} en {@code ACTIVE}. El precio mostrado
     * es {@code standardPrice}. Los holders pueden reclamar dividendos via
     * {@code DividendDistributor.claimDividends}.
     */
    OPEN,
    /**
     * Proyecto dado de baja por decision del owner/admin. Estado final.
     * On-chain: el {@code ProjectRegistry} pasa a {@code PAUSED}.
     */
    CLOSED,
    /**
     * Proyecto cancelado: ronda fallo (soft cap no alcanzado al vencer
     * {@code expectedOpenDate}) o cancelacion manual desde un estado pre-PRE_OPEN.
     * Estado final.
     */
    CANCELLED
}
