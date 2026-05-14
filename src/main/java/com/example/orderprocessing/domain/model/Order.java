package com.example.orderprocessing.domain.model;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Aggregate root representing a customer order in the order processing lifecycle.
 *
 * <p>Instances are fully immutable: every field is {@code final} and every
 * state-transition method returns a new {@code Order} instance, leaving the
 * original unchanged. This design makes concurrent reads safe without locking
 * and simplifies property-based testing (Requirements 1.1, 3.3, 6.1).
 *
 * <p>Construction is intentionally restricted to the same package so that only
 * {@link OrderBuilder} can create instances, enforcing the Builder pattern
 * (Requirement 13.1) and guaranteeing that all invariants are satisfied at
 * construction time.
 */
public final class Order {

    private final OrderId id;
    private final List<OrderItem> items;
    private final OrderStatus status;
    private final Money subtotal;
    private final Money discountTotal;
    private final Money taxTotal;
    private final Money shippingTotal;
    private final Money grandTotal;
    private final Optional<IdempotencyKey> idempotencyKey;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Optional<String> failureReason;

    /**
     * Package-private constructor — only {@link OrderBuilder} (in the same package)
     * may call this. All parameters are assumed to have been validated by the builder.
     *
     * @param id             the unique order identifier; must not be {@code null}
     * @param items          the line items; must not be {@code null} or empty
     * @param status         the current lifecycle status; must not be {@code null}
     * @param subtotal       the pre-discount item total; must not be {@code null}
     * @param discountTotal  the total discount applied; must not be {@code null}
     * @param taxTotal       the total tax applied; must not be {@code null}
     * @param shippingTotal  the shipping cost; must not be {@code null}
     * @param grandTotal     the final payable amount; must not be {@code null}
     * @param idempotencyKey the optional client-supplied deduplication key
     * @param createdAt      the wall-clock time at which the order was created; must not be {@code null}
     * @param updatedAt      the wall-clock time of the most recent update; must not be {@code null}
     * @param failureReason  the optional human-readable reason for a FAILED status
     */
    Order(
            OrderId id,
            List<OrderItem> items,
            OrderStatus status,
            Money subtotal,
            Money discountTotal,
            Money taxTotal,
            Money shippingTotal,
            Money grandTotal,
            Optional<IdempotencyKey> idempotencyKey,
            Instant createdAt,
            Instant updatedAt,
            Optional<String> failureReason) {

        this.id = id;
        this.items = Collections.unmodifiableList(List.copyOf(items));
        this.status = status;
        this.subtotal = subtotal;
        this.discountTotal = discountTotal;
        this.taxTotal = taxTotal;
        this.shippingTotal = shippingTotal;
        this.grandTotal = grandTotal;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.failureReason = failureReason;
    }

    // -------------------------------------------------------------------------
    // Immutable transition methods
    // -------------------------------------------------------------------------

    /**
     * Returns a new {@code Order} with the given status and {@code updatedAt} set to
     * the current instant. All other fields are carried over unchanged.
     *
     * @param newStatus the target lifecycle status; must not be {@code null}
     * @return a new {@code Order} instance reflecting the status change
     * @throws IllegalArgumentException if {@code newStatus} is {@code null}
     */
    public Order withStatus(OrderStatus newStatus) {
        if (newStatus == null) {
            throw new IllegalArgumentException("newStatus must not be null");
        }
        return new Order(
                id,
                items,
                newStatus,
                subtotal,
                discountTotal,
                taxTotal,
                shippingTotal,
                grandTotal,
                idempotencyKey,
                createdAt,
                Instant.now(),
                failureReason);
    }

    /**
     * Returns a new {@code Order} with all five pricing totals updated and
     * {@code updatedAt} set to the current instant. All other fields are carried
     * over unchanged.
     *
     * @param subtotal      the pre-discount item total; must not be {@code null}
     * @param discountTotal the total discount applied; must not be {@code null}
     * @param taxTotal      the total tax applied; must not be {@code null}
     * @param shippingTotal the shipping cost; must not be {@code null}
     * @param grandTotal    the final payable amount; must not be {@code null}
     * @return a new {@code Order} instance reflecting the updated totals
     * @throws IllegalArgumentException if any argument is {@code null}
     */
    public Order withTotals(
            Money subtotal,
            Money discountTotal,
            Money taxTotal,
            Money shippingTotal,
            Money grandTotal) {

        if (subtotal == null) {
            throw new IllegalArgumentException("subtotal must not be null");
        }
        if (discountTotal == null) {
            throw new IllegalArgumentException("discountTotal must not be null");
        }
        if (taxTotal == null) {
            throw new IllegalArgumentException("taxTotal must not be null");
        }
        if (shippingTotal == null) {
            throw new IllegalArgumentException("shippingTotal must not be null");
        }
        if (grandTotal == null) {
            throw new IllegalArgumentException("grandTotal must not be null");
        }
        return new Order(
                id,
                items,
                status,
                subtotal,
                discountTotal,
                taxTotal,
                shippingTotal,
                grandTotal,
                idempotencyKey,
                createdAt,
                Instant.now(),
                failureReason);
    }

    /**
     * Returns a new {@code Order} with {@code status} set to {@link OrderStatus#FAILED},
     * {@code failureReason} set to the supplied reason, and {@code updatedAt} set to
     * the current instant. All other fields are carried over unchanged.
     *
     * @param reason a human-readable description of why the order failed; must not be
     *               {@code null} or blank
     * @return a new {@code Order} instance in the FAILED state
     * @throws IllegalArgumentException if {@code reason} is {@code null} or blank
     */
    public Order withFailure(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("failure reason must not be null or blank");
        }
        return new Order(
                id,
                items,
                OrderStatus.FAILED,
                subtotal,
                discountTotal,
                taxTotal,
                shippingTotal,
                grandTotal,
                idempotencyKey,
                createdAt,
                Instant.now(),
                Optional.of(reason));
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /**
     * Returns the unique identifier of this order.
     *
     * @return the order's {@link OrderId}; never {@code null}
     */
    public OrderId getId() {
        return id;
    }

    /**
     * Returns an unmodifiable view of the line items in this order.
     *
     * @return an unmodifiable {@link List} of {@link OrderItem}; never {@code null} or empty
     */
    public List<OrderItem> getItems() {
        return items;
    }

    /**
     * Returns the current lifecycle status of this order.
     *
     * @return the {@link OrderStatus}; never {@code null}
     */
    public OrderStatus getStatus() {
        return status;
    }

    /**
     * Returns the pre-discount item subtotal.
     *
     * @return the subtotal {@link Money}; never {@code null}
     */
    public Money getSubtotal() {
        return subtotal;
    }

    /**
     * Returns the total discount applied to this order.
     *
     * @return the discount total {@link Money}; never {@code null}
     */
    public Money getDiscountTotal() {
        return discountTotal;
    }

    /**
     * Returns the total tax applied to this order.
     *
     * @return the tax total {@link Money}; never {@code null}
     */
    public Money getTaxTotal() {
        return taxTotal;
    }

    /**
     * Returns the shipping cost for this order.
     *
     * @return the shipping total {@link Money}; never {@code null}
     */
    public Money getShippingTotal() {
        return shippingTotal;
    }

    /**
     * Returns the final payable amount for this order
     * ({@code subtotal - discountTotal + taxTotal + shippingTotal}).
     *
     * @return the grand total {@link Money}; never {@code null}
     */
    public Money getGrandTotal() {
        return grandTotal;
    }

    /**
     * Returns the optional client-supplied idempotency key used to deduplicate
     * order-creation requests (Requirement 1.3).
     *
     * @return an {@link Optional} containing the {@link IdempotencyKey}, or empty
     */
    public Optional<IdempotencyKey> getIdempotencyKey() {
        return idempotencyKey;
    }

    /**
     * Returns the wall-clock instant at which this order was first created.
     *
     * @return the creation timestamp; never {@code null}
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Returns the wall-clock instant of the most recent update to this order.
     *
     * @return the last-updated timestamp; never {@code null}
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Returns the optional human-readable reason recorded when this order
     * transitioned to {@link OrderStatus#FAILED}.
     *
     * @return an {@link Optional} containing the failure reason, or empty if the
     *         order has not failed
     */
    public Optional<String> getFailureReason() {
        return failureReason;
    }

    // -------------------------------------------------------------------------
    // Object overrides
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        return "Order{id=" + id
                + ", status=" + status
                + ", grandTotal=" + grandTotal
                + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt + "}";
    }
}
