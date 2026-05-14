package com.example.orderprocessing.domain.lifecycle.states;

import com.example.orderprocessing.domain.lifecycle.OrderState;
import com.example.orderprocessing.domain.model.OrderStatus;

import java.util.Set;

/**
 * State implementation for an order in the {@link OrderStatus#SHIPPED} status.
 *
 * <p>A shipped order may only transition to {@code DELIVERED} once the carrier
 * confirms delivery, per the lifecycle graph in Requirement 6.1. Cancellation is
 * no longer permitted once the order has left the warehouse.
 */
public final class ShippedState implements OrderState {

    /** Singleton instance — state objects carry no mutable data. */
    public static final ShippedState INSTANCE = new ShippedState();

    private static final Set<OrderStatus> ALLOWED = Set.of(OrderStatus.DELIVERED);

    private ShippedState() {}

    /**
     * Returns the allowed outgoing transitions from the SHIPPED state.
     *
     * @return immutable set containing only DELIVERED
     */
    @Override
    public Set<OrderStatus> allowedTransitions() {
        return ALLOWED;
    }

    /**
     * Returns {@code false} because SHIPPED is not a terminal state.
     *
     * @return {@code false}
     */
    @Override
    public boolean isTerminal() {
        return false;
    }
}
