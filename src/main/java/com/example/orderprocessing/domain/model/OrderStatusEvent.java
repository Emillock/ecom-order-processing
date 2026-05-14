package com.example.orderprocessing.domain.model;

import java.time.Instant;

/**
 * Immutable audit record capturing a single accepted state transition for an order.
 *
 * <p>Every time an order moves from one {@link OrderStatus} to another, one
 * {@code OrderStatusEvent} is created and appended to the persistent event log
 * (Requirement 6.3).  The log is append-only; events are never mutated or deleted.
 *
 * <p>Field semantics:
 * <ul>
 *   <li>{@code orderId}  – the order this event belongs to.</li>
 *   <li>{@code from}     – the previous status; {@code null} for the initial
 *       CREATED transition (the order did not exist before).</li>
 *   <li>{@code to}       – the new status after the transition.</li>
 *   <li>{@code at}       – wall-clock timestamp at which the transition was
 *       accepted and persisted (Requirement 8.4).</li>
 *   <li>{@code actor}    – identity of the party that triggered the transition.
 *       Format: {@code "system"}, {@code "customer:{id}"}, or
 *       {@code "operator:{id}"}.</li>
 *   <li>{@code reason}   – optional human-readable explanation (e.g. cancellation
 *       reason, failure cause); may be {@code null}.</li>
 * </ul>
 */
public record OrderStatusEvent(
        OrderId orderId,
        OrderStatus from,
        OrderStatus to,
        Instant at,
        String actor,
        String reason
) {

    /**
     * Compact constructor that validates required fields.
     *
     * <p>{@code from} is intentionally allowed to be {@code null} to represent
     * the initial CREATED transition.  {@code reason} is optional and may also
     * be {@code null}.
     *
     * @throws IllegalArgumentException if {@code orderId}, {@code to}, {@code at},
     *                                  or {@code actor} is {@code null}, or if
     *                                  {@code actor} is blank.
     */
    public OrderStatusEvent {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId must not be null");
        }
        if (to == null) {
            throw new IllegalArgumentException("to status must not be null");
        }
        if (at == null) {
            throw new IllegalArgumentException("at timestamp must not be null");
        }
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("actor must not be null or blank");
        }
    }
}
