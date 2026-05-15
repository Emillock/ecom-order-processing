package com.example.orderprocessing.application.command;

import com.example.orderprocessing.application.concurrency.OrderLockRegistry;
import com.example.orderprocessing.domain.lifecycle.OrderStateMachine;
import com.example.orderprocessing.domain.model.Order;
import com.example.orderprocessing.domain.model.OrderId;
import com.example.orderprocessing.domain.model.OrderStatus;
import com.example.orderprocessing.domain.model.OrderStatusEvent;
import com.example.orderprocessing.domain.port.OrderRepository;

import java.time.Instant;

/**
 * Generic command that transitions an order to a specified target status.
 *
 * <p>This command handles any lifecycle transition not covered by the more specialised
 * {@link CreateOrderCommand} or {@link CancelOrderCommand} — for example, advancing an
 * order from CONFIRMED to SHIPPED, or from SHIPPED to DELIVERED (Requirement 6.1).
 *
 * <p>Execution is serialised under the per-{@code OrderId} lock provided by
 * {@link OrderLockRegistry} (Requirement 11.3). The command:
 * <ol>
 *   <li>Loads the current order from the repository.</li>
 *   <li>Validates the requested transition via
 *       {@link OrderStateMachine#assertTransition}.</li>
 *   <li>Transitions the order to the target status.</li>
 *   <li>Persists the updated order and appends an {@link OrderStatusEvent} to the audit log
 *       (Requirement 6.3).</li>
 * </ol>
 *
 * <p>Satisfies Requirements 6.1, 6.3, 7.3, 11.3, and 13.3.
 */
public final class TransitionOrderCommand implements OrderCommand {

    /** Actor label used when recording the system-initiated transition event. */
    private static final String SYSTEM_ACTOR = "system";

    private final OrderId orderId;
    private final OrderStatus targetStatus;
    private final String actor;
    private final String reason;
    private final OrderRepository repository;
    private final OrderLockRegistry lockRegistry;

    /**
     * Constructs a {@code TransitionOrderCommand} with all required collaborators.
     *
     * <p>Uses {@code "system"} as the actor and {@code null} as the reason. Use the
     * overloaded constructor to supply a custom actor or reason.
     *
     * @param orderId      the identifier of the order to transition; must not be {@code null}
     * @param targetStatus the target lifecycle status; must not be {@code null}
     * @param repository   the repository used to load, persist, and append status events;
     *                     must not be {@code null}
     * @param lockRegistry the per-{@code OrderId} lock registry; must not be {@code null}
     * @throws IllegalArgumentException if any argument is {@code null}
     */
    public TransitionOrderCommand(
            OrderId orderId,
            OrderStatus targetStatus,
            OrderRepository repository,
            OrderLockRegistry lockRegistry) {
        this(orderId, targetStatus, SYSTEM_ACTOR, null, repository, lockRegistry);
    }

    /**
     * Constructs a {@code TransitionOrderCommand} with a custom actor and optional reason.
     *
     * @param orderId      the identifier of the order to transition; must not be {@code null}
     * @param targetStatus the target lifecycle status; must not be {@code null}
     * @param actor        the identity of the party triggering the transition (e.g.,
     *                     {@code "system"}, {@code "operator:42"}); must not be {@code null}
     *                     or blank
     * @param reason       an optional human-readable reason for the transition; may be
     *                     {@code null}
     * @param repository   the repository used to load, persist, and append status events;
     *                     must not be {@code null}
     * @param lockRegistry the per-{@code OrderId} lock registry; must not be {@code null}
     * @throws IllegalArgumentException if {@code orderId}, {@code targetStatus},
     *                                  {@code repository}, or {@code lockRegistry} is
     *                                  {@code null}, or if {@code actor} is blank
     */
    public TransitionOrderCommand(
            OrderId orderId,
            OrderStatus targetStatus,
            String actor,
            String reason,
            OrderRepository repository,
            OrderLockRegistry lockRegistry) {

        if (orderId == null) {
            throw new IllegalArgumentException("orderId must not be null");
        }
        if (targetStatus == null) {
            throw new IllegalArgumentException("targetStatus must not be null");
        }
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("actor must not be null or blank");
        }
        if (repository == null) {
            throw new IllegalArgumentException("repository must not be null");
        }
        if (lockRegistry == null) {
            throw new IllegalArgumentException("lockRegistry must not be null");
        }

        this.orderId = orderId;
        this.targetStatus = targetStatus;
        this.actor = actor;
        this.reason = reason;
        this.repository = repository;
        this.lockRegistry = lockRegistry;
    }

    /**
     * Acquires the per-{@code OrderId} lock, validates the requested transition, applies it,
     * persists the updated order, and appends an {@link OrderStatusEvent} to the audit log.
     *
     * @return the updated {@link Order} in the target status; never {@code null}
     * @throws IllegalStateException if the order is not found or the transition is not
     *                               permitted by the lifecycle graph
     */
    @Override
    public Order execute() {
        return lockRegistry.withLock(orderId, () -> {
            Order current = repository.findById(orderId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Order not found: " + orderId.value()));

            OrderStatus fromStatus = current.getStatus();

            // Guard: validate the transition before mutating state
            OrderStateMachine.assertTransition(fromStatus, targetStatus);

            // Apply the transition
            Order transitioned = current.withStatus(targetStatus);
            Order saved = repository.save(transitioned);

            // Append the status event to the audit log (Requirement 6.3)
            OrderStatusEvent event = new OrderStatusEvent(
                    saved.getId(),
                    fromStatus,
                    targetStatus,
                    Instant.now(),
                    actor,
                    reason);
            repository.appendStatusEvent(event);

            return saved;
        });
    }

    /**
     * Returns the identifier of the order being transitioned.
     *
     * @return the {@link OrderId}; never {@code null}
     */
    public OrderId getOrderId() {
        return orderId;
    }

    /**
     * Returns the target lifecycle status for this transition.
     *
     * @return the target {@link OrderStatus}; never {@code null}
     */
    public OrderStatus getTargetStatus() {
        return targetStatus;
    }

    /**
     * Returns the actor identity that triggered this transition.
     *
     * @return the actor string; never {@code null} or blank
     */
    public String getActor() {
        return actor;
    }

    /**
     * Returns the optional human-readable reason for this transition.
     *
     * @return the reason string, or {@code null} if none was provided
     */
    public String getReason() {
        return reason;
    }
}
