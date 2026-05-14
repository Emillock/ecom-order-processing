package com.example.orderprocessing.domain.notification;

import com.example.orderprocessing.domain.model.OrderId;
import com.example.orderprocessing.domain.model.OrderStatus;

import java.time.Instant;

/**
 * Immutable event payload emitted by the {@link NotificationDispatcher} when an order
 * transitions to a new status.
 *
 * <p>Field semantics:
 * <ul>
 *   <li>{@code orderId}             – the order whose status changed.</li>
 *   <li>{@code newStatus}           – the status the order has just entered.</li>
 *   <li>{@code transitionTimestamp} – wall-clock instant at which the transition was accepted
 *       and persisted (Requirement 8.4: persistence happens before notification).</li>
 * </ul>
 *
 * <p>No Spring imports — this is a pure domain record (Requirement 14.3).
 *
 * @param orderId             the identifier of the order that changed status; must not be {@code null}
 * @param newStatus           the status the order has transitioned to; must not be {@code null}
 * @param transitionTimestamp the instant at which the transition was persisted; must not be {@code null}
 */
public record OrderNotificationEvent(
        OrderId orderId,
        OrderStatus newStatus,
        Instant transitionTimestamp
) {

    /**
     * Compact constructor that validates all required fields.
     *
     * @throws IllegalArgumentException if any field is {@code null}
     */
    public OrderNotificationEvent {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId must not be null");
        }
        if (newStatus == null) {
            throw new IllegalArgumentException("newStatus must not be null");
        }
        if (transitionTimestamp == null) {
            throw new IllegalArgumentException("transitionTimestamp must not be null");
        }
    }
}
