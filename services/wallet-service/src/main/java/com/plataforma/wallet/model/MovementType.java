package com.plataforma.wallet.model;

public enum MovementType {
    DEPOSIT,        // depósito de fondos externo
    WITHDRAWAL,     // retiro de fondos
    DIVIDEND,       // dividendo acreditado por invest-dividend-service
    TOKEN_PURCHASE, // deducción al comprar tokens de un proyecto
    P2P_SALE,       // acreditación al vender tokens en marketplace
    P2P_PURCHASE    // deducción al comprar tokens en marketplace
}
