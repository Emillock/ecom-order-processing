package com.example.orderprocessing.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * JPA entity representing a single line item within a persisted Order.
 *
 * <p>Each {@code OrderItemJpaEntity} belongs to exactly one {@link OrderJpaEntity}
 * and is cascade-managed (created, updated, deleted) alongside its parent order.
 * This satisfies the persistence requirement for order items (Requirement 1.4).
 */
@Entity
@Table(name = "order_items")
public class OrderItemJpaEntity {

    /** Primary key — UUID assigned by the application layer. */
    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "UUID")
    private UUID id;

    /** The parent order that owns this line item. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private OrderJpaEntity order;

    /** Stock Keeping Unit identifier for this line item. */
    @Column(name = "sku", nullable = false, length = 255)
    private String sku;

    /** Number of units ordered; must be >= 1. */
    @Column(name = "quantity", nullable = false)
    private int quantity;

    /** Unit price amount for this SKU. */
    @Column(name = "unit_price_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPriceAmount;

    /** ISO 4217 currency code for the unit price. */
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /** Required no-arg constructor for JPA. */
    protected OrderItemJpaEntity() {}

    /**
     * Constructs an {@code OrderItemJpaEntity} with all required fields.
     *
     * @param id              the UUID primary key
     * @param order           the parent order entity
     * @param sku             the SKU identifier
     * @param quantity        the ordered quantity (must be >= 1)
     * @param unitPriceAmount the unit price amount
     * @param currency        the ISO 4217 currency code
     */
    public OrderItemJpaEntity(UUID id, OrderJpaEntity order, String sku,
                               int quantity, BigDecimal unitPriceAmount, String currency) {
        this.id = id;
        this.order = order;
        this.sku = sku;
        this.quantity = quantity;
        this.unitPriceAmount = unitPriceAmount;
        this.currency = currency;
    }

    /** Returns the UUID primary key. */
    public UUID getId() { return id; }

    /** Sets the UUID primary key. */
    public void setId(UUID id) { this.id = id; }

    /** Returns the parent order entity. */
    public OrderJpaEntity getOrder() { return order; }

    /** Sets the parent order entity. */
    public void setOrder(OrderJpaEntity order) { this.order = order; }

    /** Returns the SKU identifier. */
    public String getSku() { return sku; }

    /** Sets the SKU identifier. */
    public void setSku(String sku) { this.sku = sku; }

    /** Returns the ordered quantity. */
    public int getQuantity() { return quantity; }

    /** Sets the ordered quantity. */
    public void setQuantity(int quantity) { this.quantity = quantity; }

    /** Returns the unit price amount. */
    public BigDecimal getUnitPriceAmount() { return unitPriceAmount; }

    /** Sets the unit price amount. */
    public void setUnitPriceAmount(BigDecimal unitPriceAmount) { this.unitPriceAmount = unitPriceAmount; }

    /** Returns the ISO 4217 currency code. */
    public String getCurrency() { return currency; }

    /** Sets the ISO 4217 currency code. */
    public void setCurrency(String currency) { this.currency = currency; }
}
