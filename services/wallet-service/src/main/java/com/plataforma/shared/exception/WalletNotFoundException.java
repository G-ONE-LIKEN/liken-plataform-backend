package com.plataforma.shared.exception;

public class WalletNotFoundException extends RuntimeException {
    public WalletNotFoundException(Long userId) {
        super("Billetera no encontrada para el usuario: " + userId);
    }
}
