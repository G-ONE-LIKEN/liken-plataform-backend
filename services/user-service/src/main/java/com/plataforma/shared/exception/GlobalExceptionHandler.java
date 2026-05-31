package com.plataforma.shared.exception;

import com.plataforma.shared.dto.ApiResponse;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private ResponseEntity<ApiResponse<Void>> response(String message, HttpStatus status) {
        return new ResponseEntity<>(ApiResponse.error(message, status.value()), status);
    }

    @ExceptionHandler({ InsufficientPermissionsException.class, UnauthorizedAccessException.class })
    public ResponseEntity<ApiResponse<Void>> handleForbidden(RuntimeException ex) {
        return response(ex.getMessage(), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<?>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(
                        "Acceso denegado: No tienes los permisos necesarios para realizar esta acción.",
                        HttpStatus.FORBIDDEN.value()));
    }

    @ExceptionHandler({ UserNotFoundException.class, RoleNotFoundException.class })
    public ResponseEntity<ApiResponse<Void>> handleNotFound(Exception ex) {
        return response(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException ex) {
        return response(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        return response(
                "No se puede eliminar el recurso porque está siendo utilizado por otros registros.",
                HttpStatus.CONFLICT);
    }

    @ExceptionHandler({ DuplicateRoleException.class, RoleInUseException.class })
    public ResponseEntity<ApiResponse<Void>> handleRoleConflict(RuntimeException ex) {
        return response(ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        return response("Ocurrió un error interno en el servidor.", HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
