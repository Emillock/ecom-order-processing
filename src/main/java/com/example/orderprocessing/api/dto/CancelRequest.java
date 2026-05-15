package com.example.orderprocessing.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for cancelling an order via {@code POST /api/v1/orders/{id}/cancel}.
 */
public record CancelRequest(

        /** The human-readable cancellation reason; must not be blank. */
        @NotBlank(message = "reason must not be blank")
        String reason
) {}
