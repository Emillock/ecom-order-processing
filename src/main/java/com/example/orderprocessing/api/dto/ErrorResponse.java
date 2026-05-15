package com.example.orderprocessing.api.dto;

import java.util.Map;

/**
 * Standard error response DTO returned by {@code GlobalExceptionHandler} for all error conditions.
 */
public record ErrorResponse(

        /** A stable machine-readable error code (e.g., {@code VALIDATION_FAILED}). */
        String code,

        /** A human-readable description of the error. */
        String message,

        /** A correlation identifier for tracing the request across logs. */
        String correlationId,

        /** Optional map of additional error details (e.g., field-level validation errors). */
        Map<String, Object> details
) {}
