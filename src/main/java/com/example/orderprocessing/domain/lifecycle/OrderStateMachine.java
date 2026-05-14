package com.example.orderprocessing.domain.lifecycle;

import com.example.orderprocessing.domain.lifecycle.states.CancelledState;
import com.example.orderprocessing.domain.lifecycle.states.ConfirmedState;
import com.example.orderprocessing.domain.lifecycle.states.CreatedState;
import com.example.orderprocessing.domain.lifecycle.states.DeliveredState;
import com.example.orderprocessing.domain.lifecycle.states.FailedState;
import com.example.orderprocessing.domain.lifecycle.states.PricedState;
import com.example.orderprocessing.domain.lifecycle.states.ReservedState;
import com.example.orderprocessing.domain.lifecycle.states.ShippedState;
import com.example.orderprocessing.domain.lifecycle.states.ValidatedState;
import com.example.orderprocessing.domain.model.OrderStatus;

import java.util.Map;

/**
 * Guard for the order lifecycle state machine (State pattern, Requirement 13.3).
 *
 * <p>Provides two static utilities:
 * <ul>
 *   <li>{@link #assertTransition(OrderStatus, OrderStatus)} — validates that a
 *       requested status transition is permitted by the lifecycle graph defined in
 *       Requirement 6.1, throwing {@link IllegalStateException} when it is not.</li>
 *   <li>{@link #stateFor(OrderStatus)} — returns the {@link OrderState} singleton
 *       that corresponds to a given {@link OrderStatus}.</li>
 * </ul>
 *
 * <p>This class is stateless and not instantiable; all members are static.
 */
public final class OrderStateMachine {

    /**
     * Immutable map from every {@link OrderStatus} to its corresponding
     * {@link OrderState} singleton, covering all 9 lifecycle states.
     */
    private static final Map<OrderStatus, OrderState> STATE_MAP = Map.of(
            OrderStatus.CREATED,   CreatedState.INSTANCE,
            OrderStatus.VALIDATED, ValidatedState.INSTANCE,
            OrderStatus.PRICED,    PricedState.INSTANCE,
            OrderStatus.RESERVED,  ReservedState.INSTANCE,
            OrderStatus.CONFIRMED, ConfirmedState.INSTANCE,
            OrderStatus.SHIPPED,   ShippedState.INSTANCE,
            OrderStatus.DELIVERED, DeliveredState.INSTANCE,
            OrderStatus.CANCELLED, CancelledState.INSTANCE,
            OrderStatus.FAILED,    FailedState.INSTANCE
    );

    private OrderStateMachine() {
        // utility class — not instantiable
    }

    /**
     * Returns the {@link OrderState} singleton for the given {@link OrderStatus}.
     *
     * @param status the status to look up; must not be {@code null}
     * @return the corresponding {@link OrderState}; never {@code null}
     * @throws IllegalArgumentException if {@code status} is {@code null}
     */
    public static OrderState stateFor(OrderStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        return STATE_MAP.get(status);
    }

    /**
     * Asserts that transitioning an order from {@code from} to {@code to} is
     * permitted by the lifecycle graph (Requirement 6.1).
     *
     * <p>Two conditions cause an {@link IllegalStateException}:
     * <ol>
     *   <li>The {@code from} state is terminal (DELIVERED, CANCELLED, or FAILED) —
     *       no outgoing transitions are allowed from terminal states (Requirement 6.4).</li>
     *   <li>The {@code to} status is not in the set of allowed transitions for
     *       {@code from} (Requirement 6.2).</li>
     * </ol>
     *
     * @param from the current {@link OrderStatus}; must not be {@code null}
     * @param to   the requested next {@link OrderStatus}; must not be {@code null}
     * @throws IllegalStateException    if the transition is not permitted
     * @throws IllegalArgumentException if either argument is {@code null}
     */
    public static void assertTransition(OrderStatus from, OrderStatus to) {
        if (from == null) {
            throw new IllegalArgumentException("from status must not be null");
        }
        if (to == null) {
            throw new IllegalArgumentException("to status must not be null");
        }

        OrderState fromState = stateFor(from);

        if (fromState.isTerminal()) {
            throw new IllegalStateException(
                    "Cannot transition from terminal state " + from + " to " + to
                            + ": no outgoing transitions are permitted from a terminal state.");
        }

        if (!fromState.allowedTransitions().contains(to)) {
            throw new IllegalStateException(
                    "Transition from " + from + " to " + to
                            + " is not permitted by the order lifecycle graph.");
        }
    }
}
