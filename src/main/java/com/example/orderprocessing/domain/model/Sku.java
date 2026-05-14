package com.example.orderprocessing.domain.model;

/**
 * Strongly-typed wrapper for a Stock Keeping Unit identifier.
 * Prevents accidental mixing of raw {@link String} values across different
 * domain concepts and makes method signatures self-documenting.
 */
public record Sku(String value) {

    /**
     * Compact constructor that rejects a {@code null} or blank SKU string.
     *
     * @throws IllegalArgumentException if {@code value} is {@code null} or blank
     */
    public Sku {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Sku value must not be null or blank");
        }
    }
}
