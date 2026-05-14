package com.example.orderprocessing.domain.lifecycle.states;

import com.example.orderprocessing.domain.lifecycle.OrderState;
import com.example.orderprocessing.domain.model.OrderStatus;

import java.util.Set;

/**
 * State implementation for an order in the {@link OrderStatus#PRICED} status.
 *
 * <p>A priced order may transition to {@code RESERVED} (inventory reserved),
 * {@code FAILED} (reservation error), or {@code CANCELLED} (explicit cancel command),
 * per the lifecycle graph in Requirement 6.1.
 */
public final class PricedState implements OrderState {

    /** Singleton instance — state objects carry no mutable data. */
    public static final PricedState INSTANCE = new PricedState();

    private static final Set<OrderStatus> ALLOWED =
            Set.of(OrderStatus.RESERVED, OrderStatus.FAILED, OrderStatus.CANCELLED);

    private PricedState() {}

    /**
     * Returns the allowed outgoing transitions from the PRICED state.
     *
     * @return immutable set containing RESERVED, FAILED, and CANCELLED
     */
    @Override
    public Set<OrderStatus> allowedTransitions() {
        return ALLOWED;
    }

    /**
     * Returns {@code false} because PRICED is not a terminal state.
     *
     * @return {@code false}
     */
    @Override
    public boolean isTerminal() {
        return false;
    }
}
