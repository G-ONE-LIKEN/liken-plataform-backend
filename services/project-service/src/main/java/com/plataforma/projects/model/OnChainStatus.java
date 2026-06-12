package com.plataforma.projects.model;

/**
 * Estado del deploy on-chain del {@code OfferingContract} asociado a un proyecto.
 *
 * <p>Lo administra el Blockchain Service:
 * <ul>
 *   <li>{@link #NOT_DEPLOYED} (default): el proyecto aun no tiene contratos on-chain.
 *       Es el estado natural mientras el proyecto esta en PENDING_APPROVAL / DRAFT.</li>
 *   <li>{@link #DEPLOYING}: el Blockchain Service inicio el deploy pero todavia no
 *       confirmaron los receipts. util para evitar reintentos concurrentes.</li>
 *   <li>{@link #DEPLOYED}: el contrato esta desplegado y verificado. Se conocen
 *       {@code offeringContractAddress}, {@code registryProjectId},
 *       {@code deployTxHash} y {@code deployBlockNumber}.</li>
 *   <li>{@link #FAILED}: el deploy fallo (out of gas, revert, etc.). Requiere
 *       intervencion manual; consultar el txHash para diagnostico.</li>
 * </ul>
 */
public enum OnChainStatus {
    NOT_DEPLOYED,
    DEPLOYING,
    DEPLOYED,
    FAILED
}
