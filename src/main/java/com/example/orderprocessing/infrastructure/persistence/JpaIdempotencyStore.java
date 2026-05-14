package com.example.orderprocessing.infrastructure.persistence;

import com.example.orderprocessing.domain.model.IdempotencyKey;
import com.example.orderprocessing.domain.model.OrderId;
import com.example.orderprocessing.domain.port.IdempotencyStore;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * JPA-backed adapter implementing the {@link IdempotencyStore} port.
 *
 * <p>Deduplicates order-creation requests by persisting a mapping from an
 * {@link IdempotencyKey} to an {@link OrderId} in the relational store.
 * The unique constraint on the {@code idempotency_key} column (defined on
 * {@link IdempotencyRecordJpaEntity}) is the database-level safety net that
 * prevents duplicate registrations under concurrent requests (Requirement 1.3).
 *
 * <p>This class is an Adapter (GoF Structural) that translates between the
 * domain port contract and the JPA persistence model.
 */
@Component
public class JpaIdempotencyStore implements IdempotencyStore {

    private final IdempotencyRecordJpaRepository repository;

    /**
     * Constructs a {@code JpaIdempotencyStore} with the given Spring Data repository.
     *
     * @param repository the JPA repository used to query and persist idempotency records
     */
    public JpaIdempotencyStore(IdempotencyRecordJpaRepository repository) {
        this.repository = repository;
    }

    /**
     * Looks up an existing order identifier associated with the given idempotency key.
     *
     * @param key the idempotency key supplied by the client; must not be {@code null}
     * @return an {@link Optional} containing the previously registered {@link OrderId}
     *         if the key has been seen before, or empty if this is a new key
     */
    @Override
    public Optional<OrderId> findExisting(IdempotencyKey key) {
        return repository.findByKey(key.value())
                .map(entity -> OrderId.of(entity.getOrderId().toString()));
    }

    /**
     * Associates an idempotency key with an order identifier after a successful order
     * creation.
     *
     * <p>If the key already exists in the store with a conflicting order identifier,
     * the underlying unique constraint will cause the database to reject the insert,
     * surfacing a {@link org.springframework.dao.DataIntegrityViolationException}.
     *
     * @param key the idempotency key to register; must not be {@code null}
     * @param id  the order identifier to associate with the key; must not be {@code null}
     */
    @Override
    public void register(IdempotencyKey key, OrderId id) {
        IdempotencyRecordJpaEntity entity = new IdempotencyRecordJpaEntity(key.value(), id.value());
        repository.save(entity);
    }
}
