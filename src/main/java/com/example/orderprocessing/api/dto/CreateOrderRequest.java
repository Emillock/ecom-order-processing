package com.example.orderprocessing.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request DTO for creating a new order via {@code POST /api/v1/orders}.
 */
public record CreateOrderRequest(

        /** The customer identifier; must not be blank. */
        @NotBlank(message = "customerId must not be blank")
        String customerId,

        /** The line items for the order; must contain at least one item. */
        @NotEmpty(message = "items must not be empty")
        @Valid
        List<OrderItemRequest> items,

        /** The shipping address for the order; must not be null. */
        @NotNull(message = "shippingAddress must not be null")
        String shippingAddress,

        /** The pricing profile to apply (e.g., "default"); must not be blank. */
        @NotBlank(message = "pricingProfile must not be blank")
        String pricingProfile
) {}
