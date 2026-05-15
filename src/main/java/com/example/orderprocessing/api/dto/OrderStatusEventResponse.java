package com.example.orderprocessing.api.dto;

import com.example.orderprocessing.domain.model.OrderStatusEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO representing a single status-transition audit event for an order.
 */
public record OrderStatusEventResponse(

        /** The unique order identifier this event belongs to. */
        UUID orderId,

        /** The previous lifecycle status; {@code null} for the initial CREATED transition. */
        String from,

        /** The new lifecycle status after the transition. */
        String to,

        /** The wall-clock instant at which the transition was accepted and persisted. */
        Instant at,

        /** The identity of the party that triggered the transition. */
        String actor,

        /** The optional human-readable reason for the transition; may be {@code null}. */
        String reason
) {

    /**
     * Factory method that maps a domain {@link OrderStatusEvent} to an {@code OrderStatusEventResponse}.
     *
     * @param event the domain event to map; must not be {@code null}
     * @return the corresponding {@code OrderStatusEventResponse}
     */
    public static OrderStatusEventResponse from(OrderStatusEvent event) {
        return new OrderStatusEventResponse(
                event.orderId().value(),
                event.from() != null ? event.from().name() : null,
                event.to().name(),
                event.at(),
                event.actor(),
                event.reason());
    }
}
