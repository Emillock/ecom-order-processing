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
 * Registry that maps every {@link OrderStatus} to its corresponding {@link OrderState}
 * singleton.
 *
 * <p>This class is the single authoritative lookup point for the State pattern
 * (Requirement 13.3). Callers obtain the state object for a given status via
 * {@link #forStatus(OrderStatus)} and then query it for allowed transitions or
 * terminal status without needing to know the concrete state class.
 *
 * <p>The registry is stateless and all methods are static; it does not need to be
 * instantiated.
 */
public final class OrderStateRegistry {

    private static final Map<OrderStatus, OrderState> REGISTRY = Map.of(
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

    private OrderStateRegistry() {
        // utility class — not instantiable
    }

    /**
     * Returns the {@link OrderState} singleton that corresponds to the given
     * {@link OrderStatus}.
     *
     * @param status the order status to look up; must not be {@code null}
     * @return the matching {@link OrderState}; never {@code null}
     * @throws IllegalArgumentException if {@code status} is {@code null} or has no
     *                                  registered state (should never happen for a
     *                                  complete enum)
     */
    public static OrderState forStatus(OrderStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        OrderState state = REGISTRY.get(status);
        if (state == null) {
            throw new IllegalArgumentException("No state registered for status: " + status);
        }
        return state;
    }
}
