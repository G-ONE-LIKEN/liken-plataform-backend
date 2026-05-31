package com.plataforma.projects.exception;

public class UnauthorizedProjectAccessException extends RuntimeException {
    public UnauthorizedProjectAccessException() {
        super("No tenés permiso para modificar este proyecto");
    }
    public UnauthorizedProjectAccessException(String message) {
        super(message);
    }
}
