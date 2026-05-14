package com.example.orderprocessing.domain.model;

/**
 * Represents the lifecycle status of an order, including terminal states.
 */
public enum OrderStatus {

    CREATED,
    VALIDATED,
    PRICED,
    RESERVED,
    CONFIRMED,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    FAILED;

    /**
     * Returns true if this status is a terminal state (no further transitions are permitted).
     * Terminal states are DELIVERED, CANCELLED, and FAILED.
     *
     * @return true for DELIVERED, CANCELLED, and FAILED; false for all other statuses
     */
    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED || this == FAILED;
    }
}
