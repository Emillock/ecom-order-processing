package com.example.orderprocessing.domain.model;

/**
 * Represents a single line item within an Order, combining a SKU, quantity, and unit price.
 *
 * <p>This is an immutable value object. The compact constructor enforces the invariant that
 * quantity must be at least 1 and that all fields are non-null, per Requirement 1.2.
 */
public record OrderItem(Sku sku, int quantity, Money unitPrice) {

    /**
     * Compact constructor that validates all fields.
     *
     * @throws IllegalArgumentException if quantity is less than 1
     * @throws NullPointerException     if sku or unitPrice is null
     */
    public OrderItem {
        if (sku == null) {
            throw new NullPointerException("sku must not be null");
        }
        if (unitPrice == null) {
            throw new NullPointerException("unitPrice must not be null");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException(
                    "quantity must be >= 1, but was: " + quantity);
        }
    }
}
