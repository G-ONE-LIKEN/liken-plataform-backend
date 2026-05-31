package com.plataforma.projects.exception;

public class ProjectNotFoundException extends RuntimeException {
    public ProjectNotFoundException(Long id) {
        super("Proyecto no encontrado con id: " + id);
    }
}
