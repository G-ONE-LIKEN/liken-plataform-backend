package com.plataforma.shared.exception;

public class DuplicateRoleException extends RuntimeException {
    public DuplicateRoleException(String name) {
        super("Ya existe un rol con el nombre: " + name);
    }
}
