package com.example.orderprocessing.domain.lifecycle.states;

import com.example.orderprocessing.domain.lifecycle.OrderState;
import com.example.orderprocessing.domain.model.OrderStatus;

import java.util.Set;

/**
 * State implementation for an order in the {@link OrderStatus#CONFIRMED} status.
 *
 * <p>A confirmed order may transition to {@code SHIPPED} (shipment dispatched) or
 * {@code CANCELLED} (explicit cancel command before shipment), per the lifecycle
 * graph in Requirement 6.1.
 *
 * <p>Note: CONFIRMED does not have a FAILED outgoing edge — once payment is
 * authorized the order is committed to fulfilment or cancellation only.
 */
public final class ConfirmedState implements OrderState {

    /** Singleton instance — state objects carry no mutable data. */
    public static final ConfirmedState INSTANCE = new ConfirmedState();

    private static final Set<OrderStatus> ALLOWED =
            Set.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED);

    private ConfirmedState() {}

    /**
     * Returns the allowed outgoing transitions from the CONFIRMED state.
     *
     * @return immutable set containing SHIPPED and CANCELLED
     */
    @Override
    public Set<OrderStatus> allowedTransitions() {
        return ALLOWED;
    }

    /**
     * Returns {@code false} because CONFIRMED is not a terminal state.
     *
     * @return {@code false}
     */
    @Override
    public boolean isTerminal() {
        return false;
    }
}
