package com.example.orderprocessing.infrastructure.cache;

import com.example.orderprocessing.domain.model.Order;
import com.example.orderprocessing.domain.model.OrderId;
import com.example.orderprocessing.domain.model.OrderStatusEvent;
import com.example.orderprocessing.domain.port.OrderCachePort;
import com.example.orderprocessing.domain.port.OrderQuery;
import com.example.orderprocessing.domain.port.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/**
 * Decorator over a delegate {@link OrderRepository} that adds a Redis cache-aside
 * layer for single-order reads (Requirement 9.3–9.5, 10.3).
 *
 * <p>Implements the Decorator structural pattern (GoF): wraps a concrete
 * {@link OrderRepository} (typically {@code JpaOrderRepository}) and intercepts
 * {@link #findById} to serve cache hits without touching the primary store, and
 * {@link #save} to evict stale cache entries after a successful write. All other
 * operations are delegated directly to the underlying repository.
 *
 * <p>When {@link OrderCachePort#isAvailable()} returns {@code false} (i.e., the
 * Resilience4j {@code "cache"} circuit breaker is OPEN), the cache is bypassed
 * entirely and a {@code cache_degraded} event is logged so that operators can
 * observe the degraded path (Requirement 10.3).
 *
 * <p>Cache failures are always swallowed — the delegate repository is the source
 * of truth and the cache is a best-effort performance layer.
 */
public class CachingOrderRepository implements OrderRepository {

    private static final Logger log = LoggerFactory.getLogger(CachingOrderRepository.class);

    private final OrderRepository delegate;
    private final OrderCachePort cache;

    /**
     * Constructs a {@code CachingOrderRepository} wrapping the given delegate repository
     * and using the supplied cache port for read-through and eviction.
     *
     * @param delegate the underlying {@link OrderRepository} (e.g., JPA); must not be {@code null}
     * @param cache    the cache port used for read-through and eviction; must not be {@code null}
     */
    public CachingOrderRepository(OrderRepository delegate, OrderCachePort cache) {
        this.delegate = delegate;
        this.cache = cache;
    }

    // -------------------------------------------------------------------------
    // OrderRepository implementation
    // -------------------------------------------------------------------------

    /**
     * Retrieves an order by its identifier using a read-through cache strategy.
     *
     * <p>When the cache is available:
     * <ol>
     *   <li>Check the cache; return immediately on a hit.</li>
     *   <li>On a miss, load from the delegate repository.</li>
     *   <li>Populate the cache with the loaded order before returning.</li>
     * </ol>
     *
     * <p>When the cache is unavailable ({@link OrderCachePort#isAvailable()} is
     * {@code false}), the cache is bypassed and a {@code cache_degraded} event is
     * logged at WARN level (Requirement 10.3).
     *
     * @param id the order identifier; must not be {@code null}
     * @return an {@link Optional} containing the order if found, or empty
     */
    @Override
    public Optional<Order> findById(OrderId id) {
        if (!cache.isAvailable()) {
            log.warn("cache_degraded: cache unavailable for findById orderId={}", id.value());
            return delegate.findById(id);
        }

        // Cache hit
        Optional<Order> cached = cache.get(id);
        if (cached.isPresent()) {
            return cached;
        }

        // Cache miss — load from delegate and populate cache
        Optional<Order> fromDelegate = delegate.findById(id);
        fromDelegate.ifPresent(order -> cache.put(id, order));
        return fromDelegate;
    }

    /**
     * Persists the order via the delegate repository and evicts the corresponding
     * cache entry so that subsequent reads reflect the updated state.
     *
     * <p>When the cache is unavailable, the eviction is skipped and a
     * {@code cache_degraded} event is logged (Requirement 10.3).
     *
     * @param order the order to persist; must not be {@code null}
     * @return the saved order as returned by the delegate
     */
    @Override
    public Order save(Order order) {
        Order saved = delegate.save(order);

        if (!cache.isAvailable()) {
            log.warn("cache_degraded: cache unavailable for evict after save orderId={}",
                    order.getId().value());
        } else {
            cache.evict(order.getId());
        }

        return saved;
    }

    /**
     * Delegates the status-event append directly to the underlying repository.
     *
     * <p>Status events are append-only audit records; no caching is applied.
     *
     * @param e the status event to append; must not be {@code null}
     */
    @Override
    public void appendStatusEvent(OrderStatusEvent e) {
        delegate.appendStatusEvent(e);
    }

    /**
     * Delegates the paginated search directly to the underlying repository.
     *
     * <p>List queries are not cached because the result set is unbounded and
     * invalidation would be prohibitively complex (Requirement 9.3).
     *
     * @param query    the filter criteria; must not be {@code null}
     * @param pageable pagination and sort parameters; must not be {@code null}
     * @return a page of matching orders; never {@code null}
     */
    @Override
    public Page<Order> search(OrderQuery query, Pageable pageable) {
        return delegate.search(query, pageable);
    }

    /**
     * Delegates the event-log retrieval directly to the underlying repository.
     *
     * @param id the order identifier; must not be {@code null}
     * @return an ordered list of {@link OrderStatusEvent} records; never {@code null}
     */
    @Override
    public java.util.List<OrderStatusEvent> findEventsByOrderId(OrderId id) {
        return delegate.findEventsByOrderId(id);
    }
}
