package com.example.orderprocessing.infrastructure.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity representing a persisted Order row in the relational store.
 *
 * <p>Uses {@code @Version} for optimistic concurrency control (Requirement 11.3) and
 * a unique constraint on {@code idempotency_key} to enforce deduplication at the
 * database level (Requirement 1.3, 1.4).
 */
@Entity
@Table(
        name = "orders",
        uniqueConstraints = {
            @UniqueConstraint(name = "uq_orders_idempotency_key", columnNames = "idempotency_key")
        })
public class OrderJpaEntity {

    /** Primary key — UUID assigned by the application layer. */
    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "UUID")
    private UUID id;

    /** Current lifecycle status stored as a string for readability. */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** Pre-discount item subtotal amount. */
    @Column(name = "subtotal_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal subtotalAmount;

    /** Total discount applied to the order. */
    @Column(name = "discount_total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountTotalAmount;

    /** Total tax applied to the order. */
    @Column(name = "tax_total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxTotalAmount;

    /** Shipping cost for the order. */
    @Column(name = "shipping_total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal shippingTotalAmount;

    /** Final payable amount (subtotal − discount + tax + shipping). */
    @Column(name = "grand_total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal grandTotalAmount;

    /** ISO 4217 currency code shared by all monetary amounts on this order. */
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /**
     * Optional client-supplied idempotency key for deduplication of order-creation
     * requests. Nullable; unique constraint enforced at the database level.
     */
    @Column(name = "idempotency_key", nullable = true, length = 255)
    private String idempotencyKey;

    /** Wall-clock instant at which the order was first created. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Wall-clock instant of the most recent update to this order. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Optional human-readable reason recorded when the order transitions to FAILED.
     * Nullable for all non-FAILED statuses.
     */
    @Column(name = "failure_reason", nullable = true, length = 1000)
    private String failureReason;

    /**
     * Optimistic-concurrency version counter managed by JPA/Hibernate.
     * Prevents lost-update anomalies when multiple threads or processes attempt
     * concurrent writes to the same order row (Requirement 11.3).
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** Line items belonging to this order. Cascaded and orphan-removed with the parent. */
    @OneToMany(
            mappedBy = "order",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<OrderItemJpaEntity> items = new ArrayList<>();

    /** Required no-arg constructor for JPA. */
    protected OrderJpaEntity() {}

    // -------------------------------------------------------------------------
    // Getters and setters
    // -------------------------------------------------------------------------

    /** Returns the order's UUID primary key. */
    public UUID getId() { return id; }

    /** Sets the order's UUID primary key. */
    public void setId(UUID id) { this.id = id; }

    /** Returns the current lifecycle status string. */
    public String getStatus() { return status; }

    /** Sets the current lifecycle status string. */
    public void setStatus(String status) { this.status = status; }

    /** Returns the pre-discount subtotal amount. */
    public BigDecimal getSubtotalAmount() { return subtotalAmount; }

    /** Sets the pre-discount subtotal amount. */
    public void setSubtotalAmount(BigDecimal subtotalAmount) { this.subtotalAmount = subtotalAmount; }

    /** Returns the total discount amount. */
    public BigDecimal getDiscountTotalAmount() { return discountTotalAmount; }

    /** Sets the total discount amount. */
    public void setDiscountTotalAmount(BigDecimal discountTotalAmount) { this.discountTotalAmount = discountTotalAmount; }

    /** Returns the total tax amount. */
    public BigDecimal getTaxTotalAmount() { return taxTotalAmount; }

    /** Sets the total tax amount. */
    public void setTaxTotalAmount(BigDecimal taxTotalAmount) { this.taxTotalAmount = taxTotalAmount; }

    /** Returns the shipping total amount. */
    public BigDecimal getShippingTotalAmount() { return shippingTotalAmount; }

    /** Sets the shipping total amount. */
    public void setShippingTotalAmount(BigDecimal shippingTotalAmount) { this.shippingTotalAmount = shippingTotalAmount; }

    /** Returns the grand total (final payable) amount. */
    public BigDecimal getGrandTotalAmount() { return grandTotalAmount; }

    /** Sets the grand total (final payable) amount. */
    public void setGrandTotalAmount(BigDecimal grandTotalAmount) { this.grandTotalAmount = grandTotalAmount; }

    /** Returns the ISO 4217 currency code. */
    public String getCurrency() { return currency; }

    /** Sets the ISO 4217 currency code. */
    public void setCurrency(String currency) { this.currency = currency; }

    /** Returns the optional idempotency key, or {@code null} if not set. */
    public String getIdempotencyKey() { return idempotencyKey; }

    /** Sets the optional idempotency key. */
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    /** Returns the creation timestamp. */
    public Instant getCreatedAt() { return createdAt; }

    /** Sets the creation timestamp. */
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    /** Returns the last-updated timestamp. */
    public Instant getUpdatedAt() { return updatedAt; }

    /** Sets the last-updated timestamp. */
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    /** Returns the optional failure reason, or {@code null} if the order has not failed. */
    public String getFailureReason() { return failureReason; }

    /** Sets the optional failure reason. */
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    /** Returns the optimistic-concurrency version counter. */
    public Long getVersion() { return version; }

    /** Sets the optimistic-concurrency version counter (managed by JPA). */
    public void setVersion(Long version) { this.version = version; }

    /** Returns the mutable list of line-item entities owned by this order. */
    public List<OrderItemJpaEntity> getItems() { return items; }

    /** Replaces the line-item list (used during upsert). */
    public void setItems(List<OrderItemJpaEntity> items) { this.items = items; }
}
