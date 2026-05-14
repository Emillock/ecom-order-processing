package com.example.orderprocessing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link OrderStatusEventJpaEntity}.
 *
 * <p>Provides the persistence operations needed by {@link JpaOrderRepository} to
 * append status-transition audit records and retrieve the full event history for
 * a given order (Requirement 6.3).
 */
public interface OrderStatusEventJpaEntityRepository
        extends JpaRepository<OrderStatusEventJpaEntity, UUID> {

    /**
     * Returns all status events for the given order, ordered by transition time ascending.
     *
     * @param orderId the UUID of the order whose events are requested
     * @return a list of matching events ordered by {@code at} ascending; never {@code null}
     */
    List<OrderStatusEventJpaEntity> findByOrderIdOrderByAtAsc(UUID orderId);
}
