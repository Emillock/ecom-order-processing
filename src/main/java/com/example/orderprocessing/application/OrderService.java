package com.example.orderprocessing.application;

import com.example.orderprocessing.application.command.CancelOrderCommand;
import com.example.orderprocessing.application.concurrency.OrderLockRegistry;
import com.example.orderprocessing.domain.model.IdempotencyKey;
import com.example.orderprocessing.domain.model.Order;
import com.example.orderprocessing.domain.model.OrderBuilder;
import com.example.orderprocessing.domain.model.OrderId;
import com.example.orderprocessing.domain.model.OrderItem;
import com.example.orderprocessing.domain.model.OrderStatus;
import com.example.orderprocessing.domain.model.OrderStatusEvent;
import com.example.orderprocessing.domain.port.IdempotencyStore;
import com.example.orderprocessing.domain.port.InventoryPort;
import com.example.orderprocessing.domain.port.OrderQuery;
import com.example.orderprocessing.domain.port.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Facade that exposes all order use-cases to the API layer, hiding pipeline,
 * lock registry, repository, and adapter details behind a single entry point.
 *
 * <p>Every state-mutating operation is executed inside
 * {@link OrderLockRegistry#withLock(OrderId, java.util.function.Supplier)} to
 * serialise concurrent mutations on the same order (Requirement 11.3).
 * Idempotency for order creation is enforced via {@link IdempotencyStore}
 * (Requirement 1.3). A structured log line is emitted on every accepted state
 * transition (Requirement 16.1).
 *
 * <p>Satisfies Requirements 1.1, 1.3, 1.4, 7.1, 7.2, 7.3, 8.4, 9.1, 13.2.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private static final String SYSTEM_ACTOR = "system";

    private final OrderRepository orderRepository;
    private final IdempotencyStore idempotencyStore;
    private final InventoryPort inventoryPort;
    private final OrderProcessingPipeline pipeline;
    private final OrderLockRegistry lockRegistry;

    /**
     * Constructs an {@code OrderService} with all required collaborators.
     *
     * @param orderRepository  the primary order persistence port; must not be {@code null}
     * @param idempotencyStore the idempotency deduplication store; must not be {@code null}
     * @param inventoryPort    the inventory reservation port; must not be {@code null}
     * @param pipeline         the order processing pipeline; must not be {@code null}
     * @param lockRegistry     the per-order striped lock registry; must not be {@code null}
     */
    public OrderService(
            OrderRepository orderRepository,
            IdempotencyStore idempotencyStore,
            InventoryPort inventoryPort,
            OrderProcessingPipeline pipeline,
            OrderLockRegistry lockRegistry) {

        if (orderRepository == null) throw new IllegalArgumentException("orderRepository must not be null");
        if (idempotencyStore == null) throw new IllegalArgumentException("idempotencyStore must not be null");
        if (inventoryPort == null) throw new IllegalArgumentException("inventoryPort must not be null");
        if (pipeline == null) throw new IllegalArgumentException("pipeline must not be null");
        if (lockRegistry == null) throw new IllegalArgumentException("lockRegistry must not be null");

        this.orderRepository = orderRepository;
        this.idempotencyStore = idempotencyStore;
        this.inventoryPort = inventoryPort;
        this.pipeline = pipeline;
        this.lockRegistry = lockRegistry;
    }

    /**
     * Creates a new order, enforcing idempotency when a key is supplied.
     *
     * <p>If the key has been seen before, the previously created order is returned
     * immediately without re-processing. Otherwise the order is built, run through
     * the pipeline, persisted, and the key is registered (Requirement 1.3, 8.4).
     *
     * @param items          the line items for the new order; must not be {@code null} or empty
     * @param customerId     the customer identifier; must not be {@code null} or blank
     * @param pricingProfile the pricing profile name to apply; must not be {@code null}
     * @param idempotencyKey an optional client-supplied deduplication key
     * @return the created (or previously created) {@link Order}; never {@code null}
     */
    public Order create(
            List<OrderItem> items,
            String customerId,
            String pricingProfile,
            Optional<IdempotencyKey> idempotencyKey) {

        // Idempotency check — return existing order if key was already registered
        if (idempotencyKey.isPresent()) {
            Optional<OrderId> existing = idempotencyStore.findExisting(idempotencyKey.get());
            if (existing.isPresent()) {
                return orderRepository.findById(existing.get())
                        .orElseThrow(() -> new IllegalStateException(
                                "Idempotency record references missing order: " + existing.get().value()));
            }
        }

        OrderId newId = OrderId.generate();

        return lockRegistry.withLock(newId, () -> {
            // Build the initial CREATED order
            OrderBuilder builder = new OrderBuilder()
                    .id(newId)
                    .items(items);
            idempotencyKey.ifPresent(builder::idempotencyKey);
            Order initial = builder.build();

            // Persist the initial CREATED order
            Order persisted = orderRepository.save(initial);

            // Record the initial CREATED event
            OrderStatusEvent createdEvent = new OrderStatusEvent(
                    persisted.getId(),
                    null,
                    OrderStatus.CREATED,
                    Instant.now(),
                    SYSTEM_ACTOR,
                    null);
            orderRepository.appendStatusEvent(createdEvent);

            // Run the pipeline (validate → price → reserve → pay → notify)
            Order processed = pipeline.run(persisted);

            // Persist the final state before notification (Requirement 8.4)
            Order saved = orderRepository.save(processed);

            // Emit structured log and append audit event for the final transition
            if (saved.getStatus() != OrderStatus.CREATED) {
                emitTransitionLog(saved.getId(), OrderStatus.CREATED, saved.getStatus(),
                        saved.getFailureReason().orElse(null));

                OrderStatusEvent finalEvent = new OrderStatusEvent(
                        saved.getId(),
                        OrderStatus.CREATED,
                        saved.getStatus(),
                        Instant.now(),
                        SYSTEM_ACTOR,
                        saved.getFailureReason().orElse(null));
                orderRepository.appendStatusEvent(finalEvent);
            }

            // Register idempotency key after successful persistence
            idempotencyKey.ifPresent(key -> idempotencyStore.register(key, saved.getId()));

            return saved;
        });
    }

    /**
     * Retrieves an order by its identifier, using the repository's cache-aside path.
     *
     * @param id the order identifier; must not be {@code null}
     * @return the {@link Order} if found
     * @throws IllegalArgumentException if {@code id} is {@code null}
     * @throws IllegalStateException    if no order exists for the given identifier
     */
    public Order get(OrderId id) {
        if (id == null) throw new IllegalArgumentException("id must not be null");
        return orderRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Order not found: " + id.value()));
    }

    /**
     * Returns a paginated list of orders matching the supplied query criteria.
     *
     * @param query    the filter criteria; must not be {@code null}
     * @param pageable pagination and sort parameters; must not be {@code null}
     * @return a page of matching orders; never {@code null}
     */
    public Page<Order> list(OrderQuery query, Pageable pageable) {
        if (query == null) throw new IllegalArgumentException("query must not be null");
        if (pageable == null) throw new IllegalArgumentException("pageable must not be null");
        return orderRepository.search(query, pageable);
    }

    /**
     * Cancels an order, releasing any held inventory reservation when applicable.
     *
     * <p>Inventory is released when the order is in {@link OrderStatus#RESERVED} or
     * {@link OrderStatus#CONFIRMED} status (Requirement 7.2). The operation is serialised
     * under the per-order lock (Requirement 11.3).
     *
     * @param id     the identifier of the order to cancel; must not be {@code null}
     * @param reason the human-readable cancellation reason; must not be {@code null} or blank
     * @return the cancelled {@link Order}; never {@code null}
     * @throws IllegalArgumentException if {@code id} or {@code reason} is invalid
     * @throws IllegalStateException    if the order is not found or the transition is not permitted
     */
    public Order cancel(OrderId id, String reason) {
        if (id == null) throw new IllegalArgumentException("id must not be null");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason must not be null or blank");

        return lockRegistry.withLock(id, () -> {
            Order current = orderRepository.findById(id)
                    .orElseThrow(() -> new IllegalStateException("Order not found: " + id.value()));

            OrderStatus fromStatus = current.getStatus();

            CancelOrderCommand cmd = new CancelOrderCommand(
                    id, reason, orderRepository, inventoryPort, lockRegistry);

            // Execute the cancel command — it acquires the lock internally, but since
            // ReentrantLock is reentrant, this is safe
            Order cancelled = cmd.execute();

            emitTransitionLog(cancelled.getId(), fromStatus, OrderStatus.CANCELLED, reason);

            return cancelled;
        });
    }

    /**
     * Returns the full audit event log for the given order.
     *
     * @param id the order identifier; must not be {@code null}
     * @return the list of {@link OrderStatusEvent} records; never {@code null}
     * @throws IllegalArgumentException if {@code id} is {@code null}
     * @throws IllegalStateException    if no order exists for the given identifier
     */
    public List<OrderStatusEvent> events(OrderId id) {
        if (id == null) throw new IllegalArgumentException("id must not be null");
        // Verify the order exists before returning events
        orderRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Order not found: " + id.value()));
        return orderRepository.findEventsByOrderId(id);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Emits a structured log line for an accepted state transition (Requirement 16.1).
     *
     * <p>The {@code correlationId} is read from MDC key {@code correlationId} when present;
     * otherwise a new UUID is generated for the log entry so every transition log line is
     * traceable even outside a request context.
     *
     * @param orderId    the order that transitioned
     * @param fromStatus the previous status
     * @param toStatus   the new status
     * @param reason     optional reason; may be {@code null}
     */
    private void emitTransitionLog(
            OrderId orderId,
            OrderStatus fromStatus,
            OrderStatus toStatus,
            String reason) {

        String correlationId = MDC.get("correlationId");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        log.info("order_transition orderId={} fromStatus={} toStatus={} timestamp={} correlationId={} actor={} reason={}",
                orderId.value(),
                fromStatus,
                toStatus,
                Instant.now(),
                correlationId,
                SYSTEM_ACTOR,
                reason != null ? reason : "");
    }
}
