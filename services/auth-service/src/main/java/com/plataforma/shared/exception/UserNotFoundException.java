package com.plataforma.shared.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(Long id) {
        super("Usuario no encontrado: " + id);
    }
    public UserNotFoundException(String message) {
        super(message);
    }
}
