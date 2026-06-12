package com.plataforma.wallet.model;

/**
 * Tipos de movimientos del ledger USD del wallet-service.
 *
 * <p>Bajo el modelo on-chain (custodia hibrida), {@code DIVIDEND},
 * {@code TOKEN_PURCHASE} y {@code REFUND} NO los origina el backend: los origina
 * el Blockchain Service indexando eventos on-chain y publicando a Kafka.
 * El wallet-service solo registra el reflejo contable del movimiento real
 * (que ya ocurrio en la chain).
 */
public enum MovementType {
    DEPOSIT,        // deposito de fondos fiat (on-ramp)
    WITHDRAWAL,     // retiro de fondos fiat (off-ramp)
    DIVIDEND,       // dividendo retirado on-chain (DividendDistributor.claimDividends)
    TOKEN_PURCHASE, // deduccion contable por compra primaria on-chain (OfferingContract.buy)
    REFUND,         // devolucion de USDC por soft cap missed (OfferingContract.refund)
    P2P_SALE,       // acreditacion al vender tokens en marketplace
    P2P_PURCHASE    // deduccion al comprar tokens en marketplace
}
