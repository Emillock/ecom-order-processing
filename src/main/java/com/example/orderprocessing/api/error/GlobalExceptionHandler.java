package com.example.orderprocessing.api.error;

import com.example.orderprocessing.api.dto.ErrorResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Centralised exception handler that maps domain and infrastructure exceptions to stable
 * HTTP error responses with machine-readable error codes (Requirements 6.2, 9.2, 12.3, 15.1–15.4).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles Bean Validation failures from {@code @Valid}-annotated request bodies.
     * Returns {@code 400 VALIDATION_FAILED} with a map of field paths to error messages.
     *
     * @param ex the validation exception raised by Spring MVC
     * @return a 400 response with field-level error details
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            details.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ErrorResponse body = new ErrorResponse(
                "VALIDATION_FAILED",
                "Request validation failed",
                correlationId(),
                details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Handles {@link OrderNotFoundException}, returning {@code 404 ORDER_NOT_FOUND}.
     *
     * @param ex the exception indicating the order was not found
     * @return a 404 response
     */
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFound(OrderNotFoundException ex) {
        ErrorResponse body = new ErrorResponse(
                "ORDER_NOT_FOUND",
                ex.getMessage(),
                correlationId(),
                null);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    /**
     * Handles {@link IdempotencyConflictException}, returning {@code 409 IDEMPOTENCY_CONFLICT}.
     *
     * @param ex the exception indicating an idempotency key conflict
     * @return a 409 response
     */
    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyConflict(IdempotencyConflictException ex) {
        ErrorResponse body = new ErrorResponse(
                "IDEMPOTENCY_CONFLICT",
                ex.getMessage(),
                correlationId(),
                null);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    /**
     * Handles {@link InvalidTransitionException}, returning {@code 409 INVALID_TRANSITION}.
     *
     * @param ex the exception indicating a disallowed lifecycle transition
     * @return a 409 response
     */
    @ExceptionHandler(InvalidTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransition(InvalidTransitionException ex) {
        ErrorResponse body = new ErrorResponse(
                "INVALID_TRANSITION",
                ex.getMessage(),
                correlationId(),
                null);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    /**
     * Handles Resilience4j {@link CallNotPermittedException} (circuit breaker OPEN),
     * returning {@code 503 DEPENDENCY_UNAVAILABLE} with the dependency name in details.
     *
     * @param ex the exception thrown when a circuit breaker is open
     * @return a 503 response with the dependency name
     */
    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ErrorResponse> handleCircuitBreakerOpen(CallNotPermittedException ex) {
        Map<String, Object> details = new HashMap<>();
        details.put("dependency", ex.getCausingCircuitBreakerName());
        ErrorResponse body = new ErrorResponse(
                "DEPENDENCY_UNAVAILABLE",
                "A required dependency is currently unavailable",
                correlationId(),
                details);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    /**
     * Catch-all handler for unexpected exceptions, returning {@code 500} with a correlation id.
     *
     * @param ex the unexpected exception
     * @return a 500 response with a correlation id for log tracing
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        String cid = correlationId();
        log.error("Unhandled exception correlationId={}", cid, ex);
        ErrorResponse body = new ErrorResponse(
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                cid,
                null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Generates a random correlation identifier for log tracing.
     *
     * @return a UUID string suitable for use as a correlation id
     */
    private static String correlationId() {
        return UUID.randomUUID().toString();
    }
}
