package com.example.orderprocessing.domain.port;

import com.example.orderprocessing.domain.model.Order;
import com.example.orderprocessing.domain.model.OrderId;
import com.example.orderprocessing.domain.model.OrderStatusEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Primary persistence port for the {@link Order} aggregate.
 *
 * <p>Implementations live in the infrastructure layer (e.g., {@code JpaOrderRepository}
 * decorated by {@code CachingOrderRepository}). The domain and application layers depend
 * only on this interface, satisfying the Dependency Inversion Principle (Requirement 14.3).
 *
 * <p>All methods must be called within an active transaction when the implementation
 * requires transactional guarantees (e.g., JPA). The application layer is responsible
 * for transaction demarcation.
 */
public interface OrderRepository {

    /**
     * Retrieves an order by its unique identifier.
     *
     * @param id the order identifier; must not be {@code null}
     * @return an {@link Optional} containing the order if found, or empty if not found
     */
    Optional<Order> findById(OrderId id);

    /**
     * Persists an order, inserting it if it does not yet exist or updating it otherwise
     * (upsert semantics).
     *
     * @param order the order to persist; must not be {@code null}
     * @return the saved order, which may differ from the input if the implementation
     *         enriches it (e.g., sets a generated version field)
     */
    Order save(Order order);

    /**
     * Appends a status-transition event to the audit log for the associated order.
     *
     * <p>This method must be called in the same transaction as the corresponding
     * {@link #save} call so that the event and the new status are committed atomically
     * (Requirement 8.4).
     *
     * @param e the status event to append; must not be {@code null}
     */
    void appendStatusEvent(OrderStatusEvent e);

    /**
     * Returns a paginated, filtered view of orders matching the supplied query criteria.
     *
     * <p>Null fields in {@code query} are treated as "match any". The returned page
     * respects the sort and page-size settings in {@code pageable}.
     *
     * @param query    the filter criteria; must not be {@code null}
     * @param pageable pagination and sort parameters; must not be {@code null}
     * @return a page of matching orders; never {@code null}
     */
    Page<Order> search(OrderQuery query, Pageable pageable);

    /**
     * Returns all status-transition events recorded for the given order, in ascending
     * chronological order (Requirement 6.3).
     *
     * @param id the order identifier; must not be {@code null}
     * @return an ordered list of {@link OrderStatusEvent} records; never {@code null}
     */
    List<OrderStatusEvent> findEventsByOrderId(OrderId id);
}
