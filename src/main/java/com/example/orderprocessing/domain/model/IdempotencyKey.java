package com.example.orderprocessing.domain.model;

/**
 * Strongly-typed wrapper for a client-supplied idempotency key.
 * The key is used by the Order_Service to deduplicate retried order-creation
 * requests within the configured retention window (Requirement 1.3).
 */
public record IdempotencyKey(String value) {

    /**
     * Compact constructor that rejects a {@code null} or blank key string.
     *
     * @throws IllegalArgumentException if {@code value} is {@code null} or blank
     */
    public IdempotencyKey {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("IdempotencyKey value must not be null or blank");
        }
    }
}
