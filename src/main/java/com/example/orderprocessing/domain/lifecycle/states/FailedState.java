package com.example.orderprocessing.domain.lifecycle.states;

import com.example.orderprocessing.domain.lifecycle.OrderState;
import com.example.orderprocessing.domain.model.OrderStatus;

import java.util.Set;

/**
 * State implementation for an order in the {@link OrderStatus#FAILED} status.
 *
 * <p>FAILED is a terminal state (Requirement 6.4). No further transitions are
 * permitted once an order has failed (e.g., due to a validation error, pricing
 * error, inventory shortage, payment decline, or dependency unavailability).
 */
public final class FailedState implements OrderState {

    /** Singleton instance — state objects carry no mutable data. */
    public static final FailedState INSTANCE = new FailedState();

    private FailedState() {}

    /**
     * Returns an empty set because FAILED is a terminal state with no outgoing
     * transitions.
     *
     * @return an empty immutable set
     */
    @Override
    public Set<OrderStatus> allowedTransitions() {
        return Set.of();
    }

    /**
     * Returns {@code true} because FAILED is a terminal state.
     *
     * @return {@code true}
     */
    @Override
    public boolean isTerminal() {
        return true;
    }
}
