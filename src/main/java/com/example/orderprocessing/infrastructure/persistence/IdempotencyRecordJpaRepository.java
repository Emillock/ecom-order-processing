package com.example.orderprocessing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link IdempotencyRecordJpaEntity}.
 *
 * <p>Provides the persistence operations needed by {@link JpaIdempotencyStore} to
 * look up and register idempotency keys for order-creation deduplication (Requirement 1.3).
 */
public interface IdempotencyRecordJpaRepository
        extends JpaRepository<IdempotencyRecordJpaEntity, String> {

    /**
     * Finds an idempotency record by its key value.
     *
     * @param key the idempotency key string to look up
     * @return an {@link Optional} containing the matching entity, or empty if not found
     */
    Optional<IdempotencyRecordJpaEntity> findByKey(String key);
}
