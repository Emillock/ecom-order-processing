package com.example.orderprocessing.api.dto;

import com.example.orderprocessing.domain.model.Order;
import com.example.orderprocessing.domain.model.OrderItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO that mirrors the full {@link Order} domain object for single-order endpoints.
 */
public record OrderResponse(

        /** The unique order identifier. */
        UUID id,

        /** The current lifecycle status. */
        String status,

        /** The line items in the order. */
        List<OrderItemResponse> items,

        /** The pre-discount item subtotal amount. */
        BigDecimal subtotal,

        /** The total discount applied. */
        BigDecimal discountTotal,

        /** The total tax applied. */
        BigDecimal taxTotal,

        /** The shipping cost. */
        BigDecimal shippingTotal,

        /** The final payable grand total. */
        BigDecimal grandTotal,

        /** The ISO 4217 currency code for all monetary amounts. */
        String currency,

        /** The client-supplied idempotency key, if any. */
        String idempotencyKey,

        /** The wall-clock instant at which the order was created. */
        Instant createdAt,

        /** The wall-clock instant of the most recent update. */
        Instant updatedAt,

        /** The human-readable failure reason, if the order is in FAILED status. */
        String failureReason
) {

    /**
     * Nested DTO representing a single line item within an {@link OrderResponse}.
     */
    public record OrderItemResponse(
            /** The stock-keeping unit identifier. */
            String sku,
            /** The quantity ordered. */
            int quantity,
            /** The unit price amount. */
            BigDecimal unitPrice,
            /** The ISO 4217 currency code for the unit price. */
            String currency
    ) {}

    /**
     * Factory method that maps a domain {@link Order} to an {@code OrderResponse}.
     *
     * @param order the domain order to map; must not be {@code null}
     * @return the corresponding {@code OrderResponse}
     */
    public static OrderResponse from(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> new OrderItemResponse(
                        item.sku().value(),
                        item.quantity(),
                        item.unitPrice().amount(),
                        item.unitPrice().currency().getCurrencyCode()))
                .toList();

        return new OrderResponse(
                order.getId().value(),
                order.getStatus().name(),
                itemResponses,
                order.getSubtotal().amount(),
                order.getDiscountTotal().amount(),
                order.getTaxTotal().amount(),
                order.getShippingTotal().amount(),
                order.getGrandTotal().amount(),
                order.getGrandTotal().currency().getCurrencyCode(),
                order.getIdempotencyKey().map(k -> k.value()).orElse(null),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getFailureReason().orElse(null));
    }
}
