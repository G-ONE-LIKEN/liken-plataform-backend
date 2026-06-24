// services/user-service/src/test/java/com/plataforma/shared/exception/GlobalExceptionHandler.java
package com.plataforma.shared.exception;

import com.plataforma.shared.dto.ApiResponse;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;

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
                        "Acceso denegado: No tienes los permisos necesarios para realizar esta accion.",
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
            "No se puede eliminar el recurso porque esta siendo utilizado por otros registros.",
            HttpStatus.CONFLICT
        );
    }

    @ExceptionHandler({ DuplicateRoleException.class, RoleInUseException.class })
    public ResponseEntity<ApiResponse<Void>> handleRoleConflict(RuntimeException ex) {
        return response(ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        return response("Ocurrio un error interno en el servidor.", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationExceptions(
        MethodArgumentNotValidException ex
    ) {
        // Esto toma el mensaje del primer error de validación que encuentre
        String message = ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return response(message, HttpStatus.BAD_REQUEST);
    }
}
