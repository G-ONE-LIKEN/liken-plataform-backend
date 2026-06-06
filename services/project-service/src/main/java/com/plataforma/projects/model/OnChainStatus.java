package com.plataforma.projects.model;

/**
 * Estado del deploy on-chain del {@code OfferingContract} asociado a un proyecto.
 *
 * <p>Lo administra el Blockchain Service:
 * <ul>
 *   <li>{@link #NOT_DEPLOYED} (default): el proyecto aún no tiene contratos on-chain.
 *       Es el estado natural mientras el proyecto está en PENDING_APPROVAL / DRAFT.</li>
 *   <li>{@link #DEPLOYING}: el Blockchain Service inició el deploy pero todavía no
 *       confirmaron los receipts. Útil para evitar reintentos concurrentes.</li>
 *   <li>{@link #DEPLOYED}: el contrato está desplegado y verificado. Se conocen
 *       {@code offeringContractAddress}, {@code registryProjectId},
 *       {@code deployTxHash} y {@code deployBlockNumber}.</li>
 *   <li>{@link #FAILED}: el deploy falló (out of gas, revert, etc.). Requiere
 *       intervención manual; consultar el txHash para diagnóstico.</li>
 * </ul>
 */
public enum OnChainStatus {
    NOT_DEPLOYED,
    DEPLOYING,
    DEPLOYED,
    FAILED
}
