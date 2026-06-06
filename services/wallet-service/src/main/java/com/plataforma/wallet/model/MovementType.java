package com.plataforma.wallet.model;

/**
 * Tipos de movimientos del ledger USD del wallet-service.
 *
 * <p>Bajo el modelo on-chain (custodia híbrida), {@code DIVIDEND},
 * {@code TOKEN_PURCHASE} y {@code REFUND} NO los origina el backend: los origina
 * el Blockchain Service indexando eventos on-chain y publicando a Kafka.
 * El wallet-service sólo registra el reflejo contable del movimiento real
 * (que ya ocurrió en la chain).
 */
public enum MovementType {
    DEPOSIT,        // depósito de fondos fiat (on-ramp)
    WITHDRAWAL,     // retiro de fondos fiat (off-ramp)
    DIVIDEND,       // dividendo retirado on-chain (DividendDistributor.claimDividends)
    TOKEN_PURCHASE, // deducción contable por compra primaria on-chain (OfferingContract.buy)
    REFUND,         // devolución de USDC por soft cap missed (OfferingContract.refund)
    P2P_SALE,       // acreditación al vender tokens en marketplace
    P2P_PURCHASE    // deducción al comprar tokens en marketplace
}
