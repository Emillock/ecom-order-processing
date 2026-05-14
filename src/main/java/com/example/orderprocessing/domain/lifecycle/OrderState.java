package com.example.orderprocessing.domain.lifecycle;

import com.example.orderprocessing.domain.model.OrderStatus;

import java.util.Set;

/**
 * Represents a single state in the order lifecycle state machine (State pattern).
 *
 * <p>Each concrete implementation encapsulates the allowed outgoing transitions
 * for one {@link OrderStatus}, keeping transition logic co-located with the state
 * rather than scattered across a switch statement. This satisfies the Open/Closed
 * Principle: adding a new state means adding a new class, not modifying existing ones
 * (Requirement 13.3, 14.2).
 *
 * <p>Implementations are singletons (see {@code INSTANCE} fields) because state
 * objects carry no mutable data — they are pure behaviour.
 */
public interface OrderState {

    /**
     * Returns the set of {@link OrderStatus} values that this state may legally
     * transition to, as defined by the lifecycle graph in Requirement 6.1.
     *
     * <p>For terminal states the returned set is empty.
     *
     * @return an immutable set of allowed next statuses; never {@code null}
     */
    Set<OrderStatus> allowedTransitions();

    /**
     * Returns {@code true} if this state has no outgoing transitions, meaning the
     * order lifecycle has reached a final resting point.
     *
     * <p>Terminal states are {@code DELIVERED}, {@code CANCELLED}, and {@code FAILED}
     * (Requirement 6.4). Once an order enters a terminal state no further transitions
     * are permitted.
     *
     * @return {@code true} for terminal states; {@code false} otherwise
     */
    boolean isTerminal();
}
