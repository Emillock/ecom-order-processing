package com.example.orderprocessing.domain.model;

import java.util.UUID;

/**
 * Strongly-typed wrapper for an order's unique identifier.
 * Using a dedicated record prevents accidental mixing of raw {@link UUID} values
 * across different domain concepts (e.g., customer IDs, item IDs).
 */
public record OrderId(UUID value) {

    /**
     * Compact constructor that rejects a {@code null} UUID.
     *
     * @throws IllegalArgumentException if {@code value} is {@code null}
     */
    public OrderId {
        if (value == null) {
            throw new IllegalArgumentException("OrderId value must not be null");
        }
    }

    /**
     * Factory method that creates a new {@code OrderId} backed by a random UUID.
     *
     * @return a new, unique {@code OrderId}
     */
    public static OrderId generate() {
        return new OrderId(UUID.randomUUID());
    }

    /**
     * Factory method that parses a UUID string into an {@code OrderId}.
     *
     * @param value the UUID string representation
     * @return an {@code OrderId} wrapping the parsed UUID
     * @throws IllegalArgumentException if the string is not a valid UUID
     */
    public static OrderId of(String value) {
        return new OrderId(UUID.fromString(value));
    }
}
