package com.example.orderprocessing.domain.port;

import com.example.orderprocessing.domain.model.Order;
import com.example.orderprocessing.domain.model.OrderId;

import java.util.Optional;

/**
 * Secondary port for the cache-aside layer that sits in front of the primary order store.
 *
 * <p>Implementations (e.g., {@code RedisOrderCache}) live in the infrastructure layer.
 * The application layer uses this port to implement the read-through / write-invalidation
 * pattern described in Requirements 9 and 10 without depending on any cache technology.
 *
 * <p>All operations are best-effort: callers must handle the case where the cache is
 * unavailable by falling back to the primary store. Use {@link #isAvailable()} to check
 * availability before attempting cache operations.
 */
public interface OrderCachePort {

    /**
     * Retrieves a cached order by its identifier.
     *
     * @param id the order identifier; must not be {@code null}
     * @return an {@link Optional} containing the cached order if present, or empty on a
     *         cache miss or when the cache is unavailable
     */
    Optional<Order> get(OrderId id);

    /**
     * Stores an order in the cache under the given identifier.
     *
     * <p>This operation is idempotent: calling {@code put} with the same {@code id} and
     * an observably identical {@code order} must leave the cache in the same state as a
     * single call (Requirement 19).
     *
     * @param id    the order identifier; must not be {@code null}
     * @param order the order to cache; must not be {@code null}
     */
    void put(OrderId id, Order order);

    /**
     * Removes the cached entry for the given order identifier, if present.
     *
     * <p>This method is a no-op when no entry exists for {@code id}.
     *
     * @param id the order identifier whose cache entry should be removed; must not be
     *           {@code null}
     */
    void evict(OrderId id);

    /**
     * Returns {@code true} when the cache is reachable and accepting operations.
     *
     * <p>Callers should check this method before attempting cache reads or writes.
     * When {@code false}, the {@code CachingOrderRepository} decorator bypasses the cache
     * and records a {@code cache_degraded} event (Requirement 10.3).
     *
     * @return {@code true} if the cache is available; {@code false} if the circuit breaker
     *         for the cache dependency is OPEN or the cache is otherwise unreachable
     */
    boolean isAvailable();
}
