package com.example.orderprocessing.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a single append-only status-transition audit record for an order.
 *
 * <p>Every accepted lifecycle transition produces one {@code OrderStatusEventJpaEntity} row.
 * Rows are never updated or deleted, satisfying the append-only audit-log requirement
 * (Requirement 6.3). The event is persisted in the same transaction as the corresponding
 * {@link OrderJpaEntity} status update (Requirement 8.4).
 */
@Entity
@Table(name = "order_status_events")
public class OrderStatusEventJpaEntity {

    /** Primary key — UUID assigned by the application layer. */
    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "UUID")
    private UUID id;

    /** The order this event belongs to. */
    @Column(name = "order_id", nullable = false, updatable = false, columnDefinition = "UUID")
    private UUID orderId;

    /**
     * The previous lifecycle status; {@code null} for the initial CREATED transition
     * when the order did not previously exist.
     */
    @Column(name = "from_status", nullable = true, updatable = false, length = 20)
    private String fromStatus;

    /** The new lifecycle status after the transition. */
    @Column(name = "to_status", nullable = false, updatable = false, length = 20)
    private String toStatus;

    /** Wall-clock instant at which the transition was accepted and persisted. */
    @Column(name = "at", nullable = false, updatable = false)
    private Instant at;

    /**
     * Identity of the party that triggered the transition.
     * Format: {@code "system"}, {@code "customer:{id}"}, or {@code "operator:{id}"}.
     */
    @Column(name = "actor", nullable = false, updatable = false, length = 255)
    private String actor;

    /** Optional human-readable explanation (e.g. cancellation reason, failure cause). */
    @Column(name = "reason", nullable = true, updatable = false, length = 1000)
    private String reason;

    /** Required no-arg constructor for JPA. */
    protected OrderStatusEventJpaEntity() {}

    /**
     * Constructs an {@code OrderStatusEventJpaEntity} with all required fields.
     *
     * @param id         the UUID primary key
     * @param orderId    the UUID of the associated order
     * @param fromStatus the previous status string, or {@code null} for the initial transition
     * @param toStatus   the new status string; must not be {@code null}
     * @param at         the transition timestamp; must not be {@code null}
     * @param actor      the actor identifier; must not be {@code null} or blank
     * @param reason     the optional reason string; may be {@code null}
     */
    public OrderStatusEventJpaEntity(UUID id, UUID orderId, String fromStatus,
                                     String toStatus, Instant at, String actor, String reason) {
        this.id = id;
        this.orderId = orderId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.at = at;
        this.actor = actor;
        this.reason = reason;
    }

    /** Returns the UUID primary key. */
    public UUID getId() { return id; }

    /** Returns the UUID of the associated order. */
    public UUID getOrderId() { return orderId; }

    /** Returns the previous status string, or {@code null} for the initial transition. */
    public String getFromStatus() { return fromStatus; }

    /** Returns the new status string after the transition. */
    public String getToStatus() { return toStatus; }

    /** Returns the wall-clock instant at which the transition was persisted. */
    public Instant getAt() { return at; }

    /** Returns the actor identifier that triggered the transition. */
    public String getActor() { return actor; }

    /** Returns the optional reason string, or {@code null} if not provided. */
    public String getReason() { return reason; }
}
