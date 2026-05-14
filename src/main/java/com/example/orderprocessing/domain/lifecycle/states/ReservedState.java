package com.example.orderprocessing.domain.lifecycle.states;

import com.example.orderprocessing.domain.lifecycle.OrderState;
import com.example.orderprocessing.domain.model.OrderStatus;

import java.util.Set;

/**
 * State implementation for an order in the {@link OrderStatus#RESERVED} status.
 *
 * <p>A reserved order may transition to {@code CONFIRMED} (payment authorized),
 * {@code FAILED} (payment declined or dependency unavailable), or {@code CANCELLED}
 * (explicit cancel command), per the lifecycle graph in Requirement 6.1.
 */
public final class ReservedState implements OrderState {

    /** Singleton instance — state objects carry no mutable data. */
    public static final ReservedState INSTANCE = new ReservedState();

    private static final Set<OrderStatus> ALLOWED =
            Set.of(OrderStatus.CONFIRMED, OrderStatus.FAILED, OrderStatus.CANCELLED);

    private ReservedState() {}

    /**
     * Returns the allowed outgoing transitions from the RESERVED state.
     *
     * @return immutable set containing CONFIRMED, FAILED, and CANCELLED
     */
    @Override
    public Set<OrderStatus> allowedTransitions() {
        return ALLOWED;
    }

    /**
     * Returns {@code false} because RESERVED is not a terminal state.
     *
     * @return {@code false}
     */
    @Override
    public boolean isTerminal() {
        return false;
    }
}
