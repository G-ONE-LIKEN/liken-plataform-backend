package com.plataforma.shared.exception;

import java.math.BigDecimal;

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(BigDecimal available, BigDecimal requested) {
        super(String.format("Saldo insuficiente. Disponible: %s, solicitado: %s", available, requested));
    }
}
