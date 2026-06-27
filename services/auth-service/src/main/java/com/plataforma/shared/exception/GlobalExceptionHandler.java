// services/auth-service/src/main/java/com/plataforma/shared/exception/GlobalExceptionHandler.java
package com.plataforma.shared.exception;

import com.plataforma.shared.dto.ApiResponse;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.validation.FieldError;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private ResponseEntity<ApiResponse<Void>> response(String message, HttpStatus status) {
        return new ResponseEntity<>(ApiResponse.error(message, status.value()), status);
    }

    private ResponseEntity<ApiResponse<Void>> response(String message, HttpStatus status, String code) {
        return new ResponseEntity<>(ApiResponse.error(message, status.value(), code), status);
    }

    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedAccessException ex) {
        return response(ex.getMessage(), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<ApiResponse<Void>> handleEmailNotVerified(EmailNotVerifiedException ex) {
        return response(ex.getMessage(), HttpStatus.FORBIDDEN, EmailNotVerifiedException.CODE);
    }

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimit(RateLimitException ex) {
        return response(ex.getMessage(), HttpStatus.TOO_MANY_REQUESTS);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(UserNotFoundException ex) {
        return response(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return response(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return response("El cuerpo de la solicitud no tiene un formato valido", HttpStatus.BAD_REQUEST);
    }

    /**
     * Circuit breaker abierto: user-service viene fallando y dejamos de
     * intentar. Fail-fast con 503 para que el cliente reintente luego.
     */
    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ApiResponse<Void>> handleCircuitOpen(CallNotPermittedException ex) {
        log.warn("Circuit breaker abierto: {}", ex.getMessage());
        return response("El servicio no está disponible en este momento. Intenta en unos segundos.",
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    /** Timeout o conexión rechazada hacia un servicio interno. */
    @ExceptionHandler({ResourceAccessException.class, HttpServerErrorException.class})
    public ResponseEntity<ApiResponse<Void>> handleDownstreamUnavailable(Exception ex) {
        log.warn("Servicio interno no disponible: {}", ex.getMessage());
        return response("El servicio no está disponible en este momento. Intenta en unos segundos.",
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        log.error("Error inesperado en auth-service", ex);
        return response("Ocurrio un error interno en el servidor.", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
        MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors()
            .stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining(", "));
        return response(message, HttpStatus.BAD_REQUEST);
    }
}
