package com.example.orderprocessing.domain.lifecycle.states;

import com.example.orderprocessing.domain.lifecycle.OrderState;
import com.example.orderprocessing.domain.model.OrderStatus;

import java.util.Set;

/**
 * State implementation for an order in the {@link OrderStatus#CREATED} status.
 *
 * <p>A newly created order may transition to {@code VALIDATED} (validation passed),
 * {@code FAILED} (validation failed), or {@code CANCELLED} (explicit cancel command),
 * per the lifecycle graph in Requirement 6.1.
 */
public final class CreatedState implements OrderState {

    /** Singleton instance — state objects carry no mutable data. */
    public static final CreatedState INSTANCE = new CreatedState();

    private static final Set<OrderStatus> ALLOWED =
            Set.of(OrderStatus.VALIDATED, OrderStatus.FAILED, OrderStatus.CANCELLED);

    private CreatedState() {}

    /**
     * Returns the allowed outgoing transitions from the CREATED state.
     *
     * @return immutable set containing VALIDATED, FAILED, and CANCELLED
     */
    @Override
    public Set<OrderStatus> allowedTransitions() {
        return ALLOWED;
    }

    /**
     * Returns {@code false} because CREATED is not a terminal state.
     *
     * @return {@code false}
     */
    @Override
    public boolean isTerminal() {
        return false;
    }
}
