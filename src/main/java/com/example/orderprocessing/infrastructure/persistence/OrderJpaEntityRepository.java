package com.example.orderprocessing.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link OrderJpaEntity}.
 *
 * <p>Provides the low-level persistence operations used by {@link JpaOrderRepository}
 * to load, save, and search order rows. Custom query methods support the paginated
 * search by status, customerId, and time range required by
 * {@link com.example.orderprocessing.domain.port.OrderRepository#search}.
 */
public interface OrderJpaEntityRepository extends JpaRepository<OrderJpaEntity, UUID> {

    /**
     * Returns a paginated list of orders matching all non-null filter criteria.
     *
     * <p>Each parameter is optional: passing {@code null} for a parameter causes that
     * filter to be skipped (match-any semantics). The JPQL uses {@code IS NULL OR}
     * guards to achieve this without dynamic query construction.
     *
     * @param status     the status string to filter on, or {@code null} for any
     * @param customerId not used in the current entity model (reserved for future use);
     *                   pass {@code null} to skip
     * @param from       inclusive lower bound on {@code createdAt}, or {@code null}
     * @param to         inclusive upper bound on {@code createdAt}, or {@code null}
     * @param pageable   pagination and sort parameters; must not be {@code null}
     * @return a page of matching {@link OrderJpaEntity} instances
     */
    @Query("SELECT o FROM OrderJpaEntity o WHERE "
            + "(:status IS NULL OR o.status = :status) AND "
            + "(:from IS NULL OR o.createdAt >= :from) AND "
            + "(:to IS NULL OR o.createdAt <= :to)")
    Page<OrderJpaEntity> search(
            @Param("status") String status,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);
}
