package com.example.orderprocessing.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Request DTO representing a single line item in a create-order request.
 */
public record OrderItemRequest(

        /** The stock-keeping unit identifier; must not be blank. */
        @NotBlank(message = "sku must not be blank")
        String sku,

        /** The requested quantity; must be at least 1. */
        @Min(value = 1, message = "quantity must be at least 1")
        int quantity,

        /** The unit price amount; must not be null. */
        @NotNull(message = "unitPrice amount must not be null")
        BigDecimal unitPrice,

        /** The ISO 4217 currency code for the unit price; must not be blank. */
        @NotBlank(message = "currency must not be blank")
        String currency
) {}
