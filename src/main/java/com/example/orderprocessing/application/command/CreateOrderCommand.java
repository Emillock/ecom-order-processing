package com.example.orderprocessing.application.command;

import com.example.orderprocessing.application.OrderProcessingPipeline;
import com.example.orderprocessing.application.concurrency.OrderLockRegistry;
import com.example.orderprocessing.domain.model.Order;
import com.example.orderprocessing.domain.model.OrderStatus;
import com.example.orderprocessing.domain.model.OrderStatusEvent;
import com.example.orderprocessing.domain.port.OrderRepository;

import java.time.Instant;

/**
 * Command that creates a new order by running it through the full processing pipeline.
 *
 * <p>Execution is serialised under the per-{@code OrderId} lock provided by
 * {@link OrderLockRegistry} to prevent concurrent creation attempts for the same order ID
 * (Requirement 11.3). After the pipeline completes, an {@link OrderStatusEvent} recording
 * the initial CREATED transition is appended to the audit log (Requirement 6.3).
 *
 * <p>The pipeline drives the order through validate → price → reserve → pay → notify.
 * If any stage fails, the order is persisted in {@link OrderStatus#FAILED} status and the
 * failure reason is recorded on the order (Requirements 2.3, 3.6, 4.5, 5.5).
 *
 * <p>Satisfies Requirements 6.3, 7.1, 11.3, and 13.3.
 */
public final class CreateOrderCommand implements OrderCommand {

    /** Actor label used when recording the system-initiated creation event. */
    private static final String SYSTEM_ACTOR = "system";

    private final Order order;
    private final OrderProcessingPipeline pipeline;
    private final OrderRepository repository;
    private final OrderLockRegistry lockRegistry;

    /**
     * Constructs a {@code CreateOrderCommand} with all required collaborators.
     *
     * @param order        the newly-built order to process; must not be {@code null}; must be
     *                     in {@link OrderStatus#CREATED} status
     * @param pipeline     the processing pipeline to run; must not be {@code null}
     * @param repository   the repository used to persist the order and append status events;
     *                     must not be {@code null}
     * @param lockRegistry the per-{@code OrderId} lock registry; must not be {@code null}
     * @throws IllegalArgumentException if any argument is {@code null}
     */
    public CreateOrderCommand(
            Order order,
            OrderProcessingPipeline pipeline,
            OrderRepository repository,
            OrderLockRegistry lockRegistry) {

        if (order == null) {
            throw new IllegalArgumentException("order must not be null");
        }
        if (pipeline == null) {
            throw new IllegalArgumentException("pipeline must not be null");
        }
        if (repository == null) {
            throw new IllegalArgumentException("repository must not be null");
        }
        if (lockRegistry == null) {
            throw new IllegalArgumentException("lockRegistry must not be null");
        }

        this.order = order;
        this.pipeline = pipeline;
        this.repository = repository;
        this.lockRegistry = lockRegistry;
    }

    /**
     * Acquires the per-{@code OrderId} lock, runs the processing pipeline, persists the
     * resulting order, and appends the initial {@link OrderStatusEvent} to the audit log.
     *
     * <p>The status event is appended after the order is persisted so that the audit log
     * always reflects a committed state (Requirement 8.4).
     *
     * @return the processed {@link Order}; never {@code null}
     */
    @Override
    public Order execute() {
        return lockRegistry.withLock(order.getId(), () -> {
            // Persist the initial CREATED order before running the pipeline
            Order persisted = repository.save(order);

            // Record the initial CREATED event
            OrderStatusEvent createdEvent = new OrderStatusEvent(
                    persisted.getId(),
                    null,
                    OrderStatus.CREATED,
                    Instant.now(),
                    SYSTEM_ACTOR,
                    null);
            repository.appendStatusEvent(createdEvent);

            // Run the full pipeline (validate → price → reserve → pay → notify)
            Order processed = pipeline.run(persisted);

            // Persist the final state (CONFIRMED or FAILED)
            Order saved = repository.save(processed);

            // Record the final status transition event
            if (saved.getStatus() != OrderStatus.CREATED) {
                OrderStatusEvent finalEvent = new OrderStatusEvent(
                        saved.getId(),
                        OrderStatus.CREATED,
                        saved.getStatus(),
                        Instant.now(),
                        SYSTEM_ACTOR,
                        saved.getFailureReason().orElse(null));
                repository.appendStatusEvent(finalEvent);
            }

            return saved;
        });
    }
}
