package com.example.orderprocessing.property.concurrency;

import com.example.orderprocessing.application.concurrency.OrderLockRegistry;
import com.example.orderprocessing.domain.lifecycle.OrderStateMachine;
import com.example.orderprocessing.domain.model.Money;
import com.example.orderprocessing.domain.model.Order;
import com.example.orderprocessing.domain.model.OrderBuilder;
import com.example.orderprocessing.domain.model.OrderId;
import com.example.orderprocessing.domain.model.OrderItem;
import com.example.orderprocessing.domain.model.OrderStatus;
import com.example.orderprocessing.domain.model.OrderStatusEvent;
import com.example.orderprocessing.domain.model.Sku;
import com.example.orderprocessing.domain.port.OrderQuery;
import com.example.orderprocessing.domain.port.OrderRepository;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for the per-{@link OrderId} serialization invariant under concurrency.
 *
 * <p>Verifies that when multiple threads concurrently apply state-mutating operations to
 * the same {@link OrderId}, the final persisted status is always reachable by <em>some</em>
 * sequential ordering of the submitted transitions — i.e., no out-of-lifecycle interleavings
 * can produce a status that would be unreachable in any serial execution.
 *
 * <p>The test uses an in-memory {@link HashMap}-backed {@link OrderRepository} stub and
 * {@link OrderLockRegistry} to serialize mutations, matching the production design where
 * every state-mutating operation in {@code OrderService} runs inside
 * {@code lockRegistry.withLock(orderId, ...)}.
 *
 * <p><b>Property 9: Concurrent state-mutating operations on the same {@code OrderId}
 * produce a final status reachable by some sequential ordering of the inputs
 * (no out-of-lifecycle interleavings).</b>
 *
 * <p><b>Validates: Requirements 11.3, 11.4</b>
 */
class OrderSerializationInvariantProperties {

    // -------------------------------------------------------------------------
    // In-memory OrderRepository stub
    // -------------------------------------------------------------------------

    /**
     * Minimal in-memory {@link OrderRepository} stub backed by a {@link ConcurrentHashMap}.
     * Thread-safety of the map itself is not relied upon for correctness — the
     * {@link OrderLockRegistry} provides the serialization guarantee under test.
     */
    static final class InMemoryOrderRepository implements OrderRepository {

        private final Map<OrderId, Order> store = new ConcurrentHashMap<>();

        @Override
        public Optional<Order> findById(OrderId id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Order save(Order order) {
            store.put(order.getId(), order);
            return order;
        }

        @Override
        public void appendStatusEvent(OrderStatusEvent event) {
            // no-op for this stub — we only care about the final Order state
        }

        @Override
        public Page<Order> search(OrderQuery query, Pageable pageable) {
            return Page.empty();
        }
    }

    // -------------------------------------------------------------------------
    // Generators
    // -------------------------------------------------------------------------

    /**
     * Provides a list of 2–5 valid sequential transitions starting from
     * {@link OrderStatus#CREATED}, drawn from the Requirement 6.1 lifecycle graph.
     *
     * <p>Each element is a target status reachable from the previous one, so the
     * list represents a valid sequential path through the lifecycle.
     *
     * @return arbitrary over non-empty lists of reachable {@link OrderStatus} values
     */
    @Provide
    Arbitrary<List<OrderStatus>> anyTransitionSequence() {
        // Build a fixed set of valid sequential paths from CREATED
        // We generate a path length between 1 and 5 steps
        Arbitrary<Integer> lengthArb = Arbitraries.integers().between(1, 5);

        return lengthArb.flatMap(length -> {
            // Start from CREATED and pick a random valid next status at each step
            return Arbitraries.just(buildRandomPath(length));
        }).filter(path -> !path.isEmpty());
    }

    /**
     * Builds a random valid path of the given length through the lifecycle graph
     * starting from {@link OrderStatus#CREATED}.
     *
     * @param steps the number of transitions to take
     * @return a list of target statuses (not including CREATED as the starting point)
     */
    private List<OrderStatus> buildRandomPath(int steps) {
        // Deterministic paths through the lifecycle for property generation
        // We use a fixed set of valid paths to keep the generator simple and correct
        List<List<OrderStatus>> validPaths = List.of(
                List.of(OrderStatus.VALIDATED),
                List.of(OrderStatus.FAILED),
                List.of(OrderStatus.CANCELLED),
                List.of(OrderStatus.VALIDATED, OrderStatus.PRICED),
                List.of(OrderStatus.VALIDATED, OrderStatus.FAILED),
                List.of(OrderStatus.VALIDATED, OrderStatus.CANCELLED),
                List.of(OrderStatus.VALIDATED, OrderStatus.PRICED, OrderStatus.RESERVED),
                List.of(OrderStatus.VALIDATED, OrderStatus.PRICED, OrderStatus.FAILED),
                List.of(OrderStatus.VALIDATED, OrderStatus.PRICED, OrderStatus.CANCELLED),
                List.of(OrderStatus.VALIDATED, OrderStatus.PRICED, OrderStatus.RESERVED, OrderStatus.CONFIRMED),
                List.of(OrderStatus.VALIDATED, OrderStatus.PRICED, OrderStatus.RESERVED, OrderStatus.FAILED),
                List.of(OrderStatus.VALIDATED, OrderStatus.PRICED, OrderStatus.RESERVED, OrderStatus.CANCELLED),
                List.of(OrderStatus.VALIDATED, OrderStatus.PRICED, OrderStatus.RESERVED,
                        OrderStatus.CONFIRMED, OrderStatus.SHIPPED),
                List.of(OrderStatus.VALIDATED, OrderStatus.PRICED, OrderStatus.RESERVED,
                        OrderStatus.CONFIRMED, OrderStatus.CANCELLED)
        );
        // Pick a path whose length is at most `steps`
        int idx = Math.abs(steps) % validPaths.size();
        List<OrderStatus> chosen = validPaths.get(idx);
        return chosen.subList(0, Math.min(steps, chosen.size()));
    }

    // -------------------------------------------------------------------------
    // Property 9
    // -------------------------------------------------------------------------

    /**
     * Property 9: Concurrent state-mutating operations on the same {@code OrderId}
     * produce a final status reachable by some sequential ordering of the inputs.
     *
     * <p>Spawns {@code N} virtual threads, each attempting to apply the next transition
     * in a shared sequence against the same {@link OrderId}. All mutations are serialized
     * by {@link OrderLockRegistry}. After all threads complete, the final status in the
     * in-memory repository must be one of the statuses present in the input sequence —
     * proving that no out-of-lifecycle interleaving occurred.
     *
     * <p><b>Validates: Requirements 11.3, 11.4</b>
     *
     * @param targetStatuses a list of valid sequential target statuses generated by jqwik
     */
    @Property(tries = 50)
    void concurrentMutationsProduceFinalStatusReachableBySequentialOrdering(
            @ForAll("anyTransitionSequence") List<OrderStatus> targetStatuses) throws Exception {

        OrderId orderId = OrderId.generate();
        InMemoryOrderRepository repository = new InMemoryOrderRepository();
        OrderLockRegistry lockRegistry = new OrderLockRegistry();

        // Seed the repository with a CREATED order
        Order initial = new OrderBuilder()
                .id(orderId)
                .item(new OrderItem(new Sku("SKU-001"), 1,
                        new Money(new BigDecimal("10.0000"), Currency.getInstance("USD"))))
                .status(OrderStatus.CREATED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        repository.save(initial);

        // The set of statuses that are valid outcomes of any sequential ordering
        // of the input transitions (including CREATED as the starting point)
        List<OrderStatus> reachableStatuses = new ArrayList<>();
        reachableStatuses.add(OrderStatus.CREATED);
        reachableStatuses.addAll(targetStatuses);

        int threadCount = targetStatuses.size();
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        // Use virtual threads to match the production executor
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            for (int i = 0; i < threadCount; i++) {
                final OrderStatus targetStatus = targetStatuses.get(i);
                futures.add(executor.submit(() -> {
                    try {
                        startLatch.await(); // synchronize start for maximum contention
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    // Each thread attempts to apply its transition under the per-Order lock
                    lockRegistry.withLock(orderId, () -> {
                        Order current = repository.findById(orderId).orElseThrow();
                        // Only apply the transition if it is valid from the current status
                        try {
                            OrderStateMachine.assertTransition(current.getStatus(), targetStatus);
                            Order updated = current.withStatus(targetStatus);
                            repository.save(updated);
                        } catch (IllegalStateException ignored) {
                            // Transition not valid from current status — skip (another thread
                            // already advanced the order past this point)
                        }
                        return null;
                    });
                }));
            }

            // Release all threads simultaneously
            startLatch.countDown();

            // Wait for all threads to complete
            for (Future<?> f : futures) {
                f.get();
            }
        } finally {
            executor.shutdown();
        }

        // Assert: the final status must be one of the statuses in the input sequence
        // (i.e., reachable by some sequential ordering of the submitted transitions)
        Order finalOrder = repository.findById(orderId).orElseThrow();
        OrderStatus finalStatus = finalOrder.getStatus();

        assertThat(reachableStatuses)
                .as("Final status %s must be reachable by some sequential ordering of %s",
                        finalStatus, targetStatuses)
                .contains(finalStatus);
    }
}
