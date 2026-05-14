package com.example.orderprocessing.domain.lifecycle.states;

import com.example.orderprocessing.domain.lifecycle.OrderState;
import com.example.orderprocessing.domain.model.OrderStatus;

import java.util.Set;

/**
 * State implementation for an order in the {@link OrderStatus#DELIVERED} status.
 *
 * <p>DELIVERED is a terminal state (Requirement 6.4). No further transitions are
 * permitted once an order has been delivered to the customer.
 */
public final class DeliveredState implements OrderState {

    /** Singleton instance — state objects carry no mutable data. */
    public static final DeliveredState INSTANCE = new DeliveredState();

    private DeliveredState() {}

    /**
     * Returns an empty set because DELIVERED is a terminal state with no outgoing
     * transitions.
     *
     * @return an empty immutable set
     */
    @Override
    public Set<OrderStatus> allowedTransitions() {
        return Set.of();
    }

    /**
     * Returns {@code true} because DELIVERED is a terminal state.
     *
     * @return {@code true}
     */
    @Override
    public boolean isTerminal() {
        return true;
    }
}
