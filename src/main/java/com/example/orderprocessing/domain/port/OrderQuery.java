package com.example.orderprocessing.domain.port;

import com.example.orderprocessing.domain.model.OrderStatus;

import java.time.Instant;

/**
 * Immutable query record used to filter orders in {@link OrderRepository#search}.
 *
 * <p>All fields are optional (nullable). Only non-null fields are applied as filter
 * predicates; a fully-null {@code OrderQuery} returns all orders (subject to pagination).
 */
public record OrderQuery(
        /** Filter by order status; {@code null} means any status. */
        OrderStatus status,

        /** Filter by customer identifier; {@code null} means any customer. */
        String customerId,

        /** Inclusive lower bound on {@code createdAt}; {@code null} means no lower bound. */
        Instant from,

        /** Inclusive upper bound on {@code createdAt}; {@code null} means no upper bound. */
        Instant to
) {}
