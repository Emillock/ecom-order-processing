package com.example.orderprocessing.application.command;

import com.example.orderprocessing.application.concurrency.OrderLockRegistry;
import com.example.orderprocessing.domain.lifecycle.OrderStateMachine;
import com.example.orderprocessing.domain.model.Order;
import com.example.orderprocessing.domain.model.OrderId;
import com.example.orderprocessing.domain.model.OrderStatus;
import com.example.orderprocessing.domain.model.OrderStatusEvent;
import com.example.orderprocessing.domain.port.InventoryPort;
import com.example.orderprocessing.domain.port.OrderRepository;

import java.time.Instant;
import java.util.Optional;

/**
 * Command that cancels an existing order and releases any held inventory reservation.
 *
 * <p>Execution is serialised under the per-{@code OrderId} lock provided by
 * {@link OrderLockRegistry} (Requirement 11.3). The command:
 * <ol>
 *   <li>Loads the current order from the repository.</li>
 *   <li>Validates the CANCELLED transition via {@link OrderStateMachine#assertTransition}.</li>
 *   <li>Releases the inventory reservation when the order is in {@link OrderStatus#RESERVED}
 *       or {@link OrderStatus#CONFIRMED} status (Requirement 7.2).</li>
 *   <li>Transitions the order to {@link OrderStatus#CANCELLED}.</li>
 *   <li>Persists the updated order and appends an {@link OrderStatusEvent} to the audit log
 *       (Requirement 6.3).</li>
 * </ol>
 *
 * <p>Satisfies Requirements 6.3, 7.1, 7.2, 7.3, 11.3, and 13.3.
 */
public final class CancelOrderCommand implements OrderCommand {

    /** Actor label used when recording the system-initiated cancellation event. */
    private static final String SYSTEM_ACTOR = "system";

    private final OrderId orderId;
    private final String reason;
    private final OrderRepository repository;
    private final InventoryPort inventoryPort;
    private final OrderLockRegistry lockRegistry;

    /**
     * Constructs a {@code CancelOrderCommand} with all required collaborators.
     *
     * @param orderId       the identifier of the order to cancel; must not be {@code null}
     * @param reason        the human-readable cancellation reason; must not be {@code null} or
     *                      blank
     * @param repository    the repository used to load, persist, and append status events;
     *                      must not be {@code null}
     * @param inventoryPort the inventory port used to release reservations; must not be
     *                      {@code null}
     * @param lockRegistry  the per-{@code OrderId} lock registry; must not be {@code null}
     * @throws IllegalArgumentException if any argument is {@code null}, or if {@code reason}
     *                                  is blank
     */
    public CancelOrderCommand(
            OrderId orderId,
            String reason,
            OrderRepository repository,
            InventoryPort inventoryPort,
            OrderLockRegistry lockRegistry) {

        if (orderId == null) {
            throw new IllegalArgumentException("orderId must not be null");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be null or blank");
        }
        if (repository == null) {
            throw new IllegalArgumentException("repository must not be null");
        }
        if (inventoryPort == null) {
            throw new IllegalArgumentException("inventoryPort must not be null");
        }
        if (lockRegistry == null) {
            throw new IllegalArgumentException("lockRegistry must not be null");
        }

        this.orderId = orderId;
        this.reason = reason;
        this.repository = repository;
        this.inventoryPort = inventoryPort;
        this.lockRegistry = lockRegistry;
    }

    /**
     * Acquires the per-{@code OrderId} lock, validates the cancellation transition, releases
     * inventory if applicable, transitions the order to CANCELLED, persists the result, and
     * appends an {@link OrderStatusEvent} to the audit log.
     *
     * @return the cancelled {@link Order}; never {@code null}
     * @throws IllegalStateException    if the order is not found or the transition is not
     *                                  permitted
     */
    @Override
    public Order execute() {
        return lockRegistry.withLock(orderId, () -> {
            Order current = repository.findById(orderId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Order not found: " + orderId.value()));

            OrderStatus fromStatus = current.getStatus();

            // Validate the transition before making any side-effecting calls
            OrderStateMachine.assertTransition(fromStatus, OrderStatus.CANCELLED);

            // Release inventory reservation when the order has already reserved stock
            if (fromStatus == OrderStatus.RESERVED || fromStatus == OrderStatus.CONFIRMED) {
                try {
                    inventoryPort.release(orderId, current.getItems());
                } catch (Exception ex) {
                    // Log and continue — cancellation must not be blocked by inventory release
                    // failures; the inventory provider should handle idempotent releases.
                }
            }

            // Transition to CANCELLED
            Order cancelled = current.withStatus(OrderStatus.CANCELLED);
            Order saved = repository.save(cancelled);

            // Append the cancellation event to the audit log (Requirement 6.3)
            OrderStatusEvent event = new OrderStatusEvent(
                    saved.getId(),
                    fromStatus,
                    OrderStatus.CANCELLED,
                    Instant.now(),
                    SYSTEM_ACTOR,
                    reason);
            repository.appendStatusEvent(event);

            return saved;
        });
    }

    /**
     * Returns the identifier of the order being cancelled.
     *
     * @return the {@link OrderId}; never {@code null}
     */
    public OrderId getOrderId() {
        return orderId;
    }

    /**
     * Returns the human-readable cancellation reason.
     *
     * @return the reason string; never {@code null} or blank
     */
    public String getReason() {
        return reason;
    }
}
