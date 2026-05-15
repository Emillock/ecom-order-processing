package com.example.orderprocessing.application.concurrency;

import com.example.orderprocessing.domain.model.OrderId;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Per-{@link OrderId} striped lock registry that serialises state-mutating
 * operations on the same order while allowing concurrent operations on
 * different orders.
 *
 * <p>Each {@link OrderId} is mapped to a dedicated {@link ReentrantLock} stored
 * in a {@link ConcurrentHashMap}. Callers acquire the lock via
 * {@link #withLock(OrderId, Supplier)}, which guarantees release in a
 * {@code finally} block regardless of whether the body throws.
 *
 * <p>{@link ReentrantLock} is used instead of {@code synchronized} because it
 * does not pin the carrier thread in Java 21 virtual-thread environments
 * (Requirement 11.3, 11.4).
 *
 * <p>Read operations do <em>not</em> take the lock; they rely on the
 * cache-aside read path and optimistic locking ({@code @Version}) at the
 * persistence layer as a second-line defence.
 */
@Component
public final class OrderLockRegistry {

    /** Stripe map: one {@link ReentrantLock} per live {@link OrderId}. */
    private final ConcurrentHashMap<OrderId, ReentrantLock> stripes = new ConcurrentHashMap<>();

    /**
     * Acquires the {@link ReentrantLock} for the given {@code orderId}, executes
     * {@code body}, releases the lock, and returns the result.
     *
     * <p>The lock is always released in a {@code finally} block, so exceptions
     * thrown by {@code body} propagate to the caller without leaving the lock held.
     *
     * @param <T>     the return type of the body
     * @param orderId the order whose lock should be acquired; must not be {@code null}
     * @param body    the critical section to execute under the lock; must not be {@code null}
     * @return the value returned by {@code body}
     * @throws IllegalArgumentException if {@code orderId} or {@code body} is {@code null}
     */
    public <T> T withLock(OrderId orderId, Supplier<T> body) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId must not be null");
        }
        if (body == null) {
            throw new IllegalArgumentException("body must not be null");
        }

        ReentrantLock lock = stripes.computeIfAbsent(orderId, k -> new ReentrantLock());
        lock.lock();
        try {
            return body.get();
        } finally {
            lock.unlock();
        }
    }
}
