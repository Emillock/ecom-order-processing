package com.example.orderprocessing.api.dto;

import com.example.orderprocessing.domain.model.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight response DTO for order list endpoints, omitting item details and totals breakdown.
 */
public record OrderSummary(

        /** The unique order identifier. */
        UUID id,

        /** The current lifecycle status. */
        String status,

        /** The final payable grand total. */
        BigDecimal grandTotal,

        /** The ISO 4217 currency code. */
        String currency,

        /** The number of line items in the order. */
        int itemCount,

        /** The wall-clock instant at which the order was created. */
        Instant createdAt,

        /** The wall-clock instant of the most recent update. */
        Instant updatedAt
) {

    /**
     * Factory method that maps a domain {@link Order} to an {@code OrderSummary}.
     *
     * @param order the domain order to map; must not be {@code null}
     * @return the corresponding {@code OrderSummary}
     */
    public static OrderSummary from(Order order) {
        return new OrderSummary(
                order.getId().value(),
                order.getStatus().name(),
                order.getGrandTotal().amount(),
                order.getGrandTotal().currency().getCurrencyCode(),
                order.getItems().size(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}
