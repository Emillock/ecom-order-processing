package com.example.orderprocessing.domain.lifecycle.states;

import com.example.orderprocessing.domain.lifecycle.OrderState;
import com.example.orderprocessing.domain.model.OrderStatus;

import java.util.Set;

/**
 * State implementation for an order in the {@link OrderStatus#VALIDATED} status.
 *
 * <p>A validated order may transition to {@code PRICED} (pricing completed),
 * {@code FAILED} (pricing error), or {@code CANCELLED} (explicit cancel command),
 * per the lifecycle graph in Requirement 6.1.
 */
public final class ValidatedState implements OrderState {

    /** Singleton instance — state objects carry no mutable data. */
    public static final ValidatedState INSTANCE = new ValidatedState();

    private static final Set<OrderStatus> ALLOWED =
            Set.of(OrderStatus.PRICED, OrderStatus.FAILED, OrderStatus.CANCELLED);

    private ValidatedState() {}

    /**
     * Returns the allowed outgoing transitions from the VALIDATED state.
     *
     * @return immutable set containing PRICED, FAILED, and CANCELLED
     */
    @Override
    public Set<OrderStatus> allowedTransitions() {
        return ALLOWED;
    }

    /**
     * Returns {@code false} because VALIDATED is not a terminal state.
     *
     * @return {@code false}
     */
    @Override
    public boolean isTerminal() {
        return false;
    }
}
