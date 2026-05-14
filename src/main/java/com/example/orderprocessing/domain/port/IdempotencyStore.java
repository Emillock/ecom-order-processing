package com.example.orderprocessing.domain.port;

import com.example.orderprocessing.domain.model.IdempotencyKey;
import com.example.orderprocessing.domain.model.OrderId;

import java.util.Optional;

/**
 * Secondary port for idempotency deduplication of order-creation requests.
 *
 * <p>When a client supplies an {@code Idempotency-Key} header, the application layer
 * checks this store before processing the request. If a matching key is found, the
 * existing {@link OrderId} is returned and no new order is created (Requirement 1.3).
 *
 * <p>Implementations (e.g., {@code JpaIdempotencyStore}) live in the infrastructure layer.
 * The uniqueness constraint on the key must be enforced at the persistence level to
 * prevent duplicate registrations under concurrent requests.
 */
public interface IdempotencyStore {

    /**
     * Looks up an existing order identifier associated with the given idempotency key.
     *
     * @param key the idempotency key supplied by the client; must not be {@code null}
     * @return an {@link Optional} containing the previously registered {@link OrderId}
     *         if the key has been seen before, or empty if this is a new key
     */
    Optional<OrderId> findExisting(IdempotencyKey key);

    /**
     * Associates an idempotency key with an order identifier after a successful order
     * creation.
     *
     * <p>Implementations must enforce uniqueness on {@code key}: a second call with the
     * same key must either be a no-op (if the stored value matches {@code id}) or throw
     * an exception (if the stored value conflicts with {@code id}).
     *
     * @param key the idempotency key to register; must not be {@code null}
     * @param id  the order identifier to associate with the key; must not be {@code null}
     */
    void register(IdempotencyKey key, OrderId id);
}
