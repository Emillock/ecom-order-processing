package com.example.orderprocessing.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a persisted idempotency record used to deduplicate
 * order-creation requests (Requirement 1.3).
 *
 * <p>A unique constraint on {@code key} is enforced at the database level so that
 * concurrent requests with the same idempotency key cannot both succeed in registering
 * a new order — the second insert will be rejected by the database.
 */
@Entity
@Table(
        name = "idempotency_records",
        uniqueConstraints = {
            @UniqueConstraint(name = "uq_idempotency_records_key", columnNames = "key")
        })
public class IdempotencyRecordJpaEntity {

    /**
     * The client-supplied idempotency key string; used as the primary key so that
     * lookups by key are a direct primary-key lookup rather than a secondary index scan.
     */
    @Id
    @Column(name = "key", nullable = false, updatable = false, length = 255)
    private String key;

    /** The UUID of the order that was created for this idempotency key. */
    @Column(name = "order_id", nullable = false, updatable = false, columnDefinition = "UUID")
    private UUID orderId;

    /** Wall-clock instant at which this record was first registered. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Required no-arg constructor for JPA. */
    protected IdempotencyRecordJpaEntity() {}

    /**
     * Constructs an {@code IdempotencyRecordJpaEntity} with the given key and order ID.
     * Sets {@code createdAt} to the current instant.
     *
     * @param key     the idempotency key string; must not be {@code null} or blank
     * @param orderId the UUID of the associated order; must not be {@code null}
     */
    public IdempotencyRecordJpaEntity(String key, UUID orderId) {
        this.key = key;
        this.orderId = orderId;
        this.createdAt = Instant.now();
    }

    /** Returns the idempotency key string (primary key). */
    public String getKey() { return key; }

    /** Returns the UUID of the associated order. */
    public UUID getOrderId() { return orderId; }

    /** Returns the instant at which this record was registered. */
    public Instant getCreatedAt() { return createdAt; }
}
