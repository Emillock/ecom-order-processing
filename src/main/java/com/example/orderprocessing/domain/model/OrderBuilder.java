package com.example.orderprocessing.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

/**
 * Fluent builder for constructing {@link Order} aggregate instances.
 *
 * <p>Enforces the following invariants at {@link #build()} time:
 * <ul>
 *   <li>{@code id} must be set.</li>
 *   <li>{@code items} must contain at least one {@link OrderItem}.</li>
 * </ul>
 *
 * <p>Defaults applied automatically when not explicitly set:
 * <ul>
 *   <li>{@code status} defaults to {@link OrderStatus#CREATED}.</li>
 *   <li>{@code createdAt} and {@code updatedAt} default to {@link Instant#now()}.</li>
 *   <li>{@code idempotencyKey} defaults to {@link Optional#empty()}.</li>
 *   <li>{@code failureReason} defaults to {@link Optional#empty()}.</li>
 *   <li>All {@link Money} totals default to {@link Money#zero(Currency)} in the
 *       configured currency (USD if not specified via {@link #currency(Currency)}).</li>
 * </ul>
 *
 * <p>This builder resides in the same package as {@link Order} so it can invoke
 * the package-private constructor, satisfying Requirement 13.1 (Builder pattern).
 */
public final class OrderBuilder {

    /** Fallback currency used when no currency is explicitly configured. */
    private static final Currency DEFAULT_CURRENCY = Currency.getInstance("USD");

    private OrderId id;
    private final List<OrderItem> items = new ArrayList<>();
    private OrderStatus status;
    private Currency currency = DEFAULT_CURRENCY;
    private Money subtotal;
    private Money discountTotal;
    private Money taxTotal;
    private Money shippingTotal;
    private Money grandTotal;
    private Optional<IdempotencyKey> idempotencyKey = Optional.empty();
    private Instant createdAt;
    private Instant updatedAt;
    private Optional<String> failureReason = Optional.empty();

    /**
     * Creates a new, empty {@code OrderBuilder}.
     */
    public OrderBuilder() {
        // no-arg constructor; all fields have defaults or are set via fluent setters
    }

    // -------------------------------------------------------------------------
    // Required fields
    // -------------------------------------------------------------------------

    /**
     * Sets the unique identifier for the order being built.
     *
     * @param id the order identifier; must not be {@code null}
     * @return this builder for chaining
     * @throws IllegalArgumentException if {@code id} is {@code null}
     */
    public OrderBuilder id(OrderId id) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        this.id = id;
        return this;
    }

    /**
     * Adds a single line item to the order.
     *
     * @param item the line item to add; must not be {@code null}
     * @return this builder for chaining
     * @throws IllegalArgumentException if {@code item} is {@code null}
     */
    public OrderBuilder item(OrderItem item) {
        if (item == null) {
            throw new IllegalArgumentException("item must not be null");
        }
        this.items.add(item);
        return this;
    }

    /**
     * Replaces the current item list with the supplied list.
     *
     * @param items the line items; must not be {@code null}
     * @return this builder for chaining
     * @throws IllegalArgumentException if {@code items} is {@code null}
     */
    public OrderBuilder items(List<OrderItem> items) {
        if (items == null) {
            throw new IllegalArgumentException("items must not be null");
        }
        this.items.clear();
        this.items.addAll(items);
        return this;
    }

    // -------------------------------------------------------------------------
    // Optional / defaulted fields
    // -------------------------------------------------------------------------

    /**
     * Sets the lifecycle status. Defaults to {@link OrderStatus#CREATED} if not called.
     *
     * @param status the desired status; must not be {@code null}
     * @return this builder for chaining
     * @throws IllegalArgumentException if {@code status} is {@code null}
     */
    public OrderBuilder status(OrderStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        this.status = status;
        return this;
    }

    /**
     * Sets the currency used to derive zero-valued {@link Money} defaults for all
     * totals that are not explicitly provided. Defaults to USD if not called.
     *
     * @param currency the ISO 4217 currency; must not be {@code null}
     * @return this builder for chaining
     * @throws IllegalArgumentException if {@code currency} is {@code null}
     */
    public OrderBuilder currency(Currency currency) {
        if (currency == null) {
            throw new IllegalArgumentException("currency must not be null");
        }
        this.currency = currency;
        return this;
    }

    /**
     * Sets the pre-discount item subtotal. Defaults to {@link Money#zero(Currency)}
     * in the configured currency if not called.
     *
     * @param subtotal the subtotal; must not be {@code null}
     * @return this builder for chaining
     * @throws IllegalArgumentException if {@code subtotal} is {@code null}
     */
    public OrderBuilder subtotal(Money subtotal) {
        if (subtotal == null) {
            throw new IllegalArgumentException("subtotal must not be null");
        }
        this.subtotal = subtotal;
        return this;
    }

    /**
     * Sets the total discount applied to the order. Defaults to {@link Money#zero(Currency)}
     * in the configured currency if not called.
     *
     * @param discountTotal the discount total; must not be {@code null}
     * @return this builder for chaining
     * @throws IllegalArgumentException if {@code discountTotal} is {@code null}
     */
    public OrderBuilder discountTotal(Money discountTotal) {
        if (discountTotal == null) {
            throw new IllegalArgumentException("discountTotal must not be null");
        }
        this.discountTotal = discountTotal;
        return this;
    }

    /**
     * Sets the total tax applied to the order. Defaults to {@link Money#zero(Currency)}
     * in the configured currency if not called.
     *
     * @param taxTotal the tax total; must not be {@code null}
     * @return this builder for chaining
     * @throws IllegalArgumentException if {@code taxTotal} is {@code null}
     */
    public OrderBuilder taxTotal(Money taxTotal) {
        if (taxTotal == null) {
            throw new IllegalArgumentException("taxTotal must not be null");
        }
        this.taxTotal = taxTotal;
        return this;
    }

    /**
     * Sets the shipping cost for the order. Defaults to {@link Money#zero(Currency)}
     * in the configured currency if not called.
     *
     * @param shippingTotal the shipping total; must not be {@code null}
     * @return this builder for chaining
     * @throws IllegalArgumentException if {@code shippingTotal} is {@code null}
     */
    public OrderBuilder shippingTotal(Money shippingTotal) {
        if (shippingTotal == null) {
            throw new IllegalArgumentException("shippingTotal must not be null");
        }
        this.shippingTotal = shippingTotal;
        return this;
    }

    /**
     * Sets the final payable grand total. Defaults to {@link Money#zero(Currency)}
     * in the configured currency if not called.
     *
     * @param grandTotal the grand total; must not be {@code null}
     * @return this builder for chaining
     * @throws IllegalArgumentException if {@code grandTotal} is {@code null}
     */
    public OrderBuilder grandTotal(Money grandTotal) {
        if (grandTotal == null) {
            throw new IllegalArgumentException("grandTotal must not be null");
        }
        this.grandTotal = grandTotal;
        return this;
    }

    /**
     * Sets the client-supplied idempotency key. Defaults to {@link Optional#empty()}
     * if not called.
     *
     * @param idempotencyKey the idempotency key; must not be {@code null}
     * @return this builder for chaining
     * @throws IllegalArgumentException if {@code idempotencyKey} is {@code null}
     */
    public OrderBuilder idempotencyKey(IdempotencyKey idempotencyKey) {
        if (idempotencyKey == null) {
            throw new IllegalArgumentException("idempotencyKey must not be null");
        }
        this.idempotencyKey = Optional.of(idempotencyKey);
        return this;
    }

    /**
     * Sets the creation timestamp. Defaults to {@link Instant#now()} at build time
     * if not called.
     *
     * @param createdAt the creation instant; must not be {@code null}
     * @return this builder for chaining
     * @throws IllegalArgumentException if {@code createdAt} is {@code null}
     */
    public OrderBuilder createdAt(Instant createdAt) {
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
        this.createdAt = createdAt;
        return this;
    }

    /**
     * Sets the last-updated timestamp. Defaults to {@link Instant#now()} at build time
     * if not called.
     *
     * @param updatedAt the last-updated instant; must not be {@code null}
     * @return this builder for chaining
     * @throws IllegalArgumentException if {@code updatedAt} is {@code null}
     */
    public OrderBuilder updatedAt(Instant updatedAt) {
        if (updatedAt == null) {
            throw new IllegalArgumentException("updatedAt must not be null");
        }
        this.updatedAt = updatedAt;
        return this;
    }

    /**
     * Sets the human-readable failure reason. Defaults to {@link Optional#empty()}
     * if not called.
     *
     * @param failureReason the failure reason string; must not be {@code null} or blank
     * @return this builder for chaining
     * @throws IllegalArgumentException if {@code failureReason} is {@code null} or blank
     */
    public OrderBuilder failureReason(String failureReason) {
        if (failureReason == null || failureReason.isBlank()) {
            throw new IllegalArgumentException("failureReason must not be null or blank");
        }
        this.failureReason = Optional.of(failureReason);
        return this;
    }

    // -------------------------------------------------------------------------
    // Build
    // -------------------------------------------------------------------------

    /**
     * Validates all required fields and constructs an immutable {@link Order} instance.
     *
     * <p>Required fields: {@code id} and at least one {@code item}.
     * All other fields fall back to their documented defaults.
     *
     * @return a fully constructed, immutable {@link Order}
     * @throws IllegalStateException if {@code id} is not set or {@code items} is empty
     */
    public Order build() {
        if (id == null) {
            throw new IllegalStateException("Order id is required");
        }
        if (items.isEmpty()) {
            throw new IllegalStateException("Order must contain at least one item");
        }

        // Apply defaults for optional / defaulted fields
        OrderStatus resolvedStatus = (status != null) ? status : OrderStatus.CREATED;
        Instant resolvedCreatedAt = (createdAt != null) ? createdAt : Instant.now();
        Instant resolvedUpdatedAt = (updatedAt != null) ? updatedAt : Instant.now();
        Money resolvedSubtotal = (subtotal != null) ? subtotal : Money.zero(currency);
        Money resolvedDiscountTotal = (discountTotal != null) ? discountTotal : Money.zero(currency);
        Money resolvedTaxTotal = (taxTotal != null) ? taxTotal : Money.zero(currency);
        Money resolvedShippingTotal = (shippingTotal != null) ? shippingTotal : Money.zero(currency);
        Money resolvedGrandTotal = (grandTotal != null) ? grandTotal : Money.zero(currency);

        return new Order(
                id,
                items,
                resolvedStatus,
                resolvedSubtotal,
                resolvedDiscountTotal,
                resolvedTaxTotal,
                resolvedShippingTotal,
                resolvedGrandTotal,
                idempotencyKey,
                resolvedCreatedAt,
                resolvedUpdatedAt,
                failureReason);
    }
}
