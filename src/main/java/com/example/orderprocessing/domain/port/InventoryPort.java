package com.example.orderprocessing.domain.port;

import com.example.orderprocessing.domain.model.OrderId;
import com.example.orderprocessing.domain.model.OrderItem;

import java.util.List;

/**
 * Secondary port for the external inventory provider.
 *
 * <p>Implementations (e.g., {@code HttpInventoryAdapter}) live in the infrastructure layer
 * and translate between the domain model and the external HTTP/JSON contract. The domain
 * and application layers depend only on this interface (Requirement 14.3, 13.5).
 *
 * <p>Implementations must be annotated with a Resilience4j circuit breaker named
 * {@code "inventory"} so that failures past the configured threshold cause the breaker to
 * open and the pipeline to transition the order to {@code FAILED} with reason
 * {@code dependency_unavailable:inventory} (Requirements 4.4, 4.5, 12.1).
 */
public interface InventoryPort {

    /**
     * Attempts to reserve stock for all items in the given order.
     *
     * <p>A successful reservation holds the requested quantities until either
     * {@link #release} is called or the reservation expires on the provider side.
     *
     * @param id    the order identifier for which stock is being reserved; must not be
     *              {@code null}
     * @param items the line items whose quantities must be reserved; must not be
     *              {@code null} or empty
     * @return a {@link ReservationResult} indicating success or the SKUs that are
     *         out of stock; never {@code null}
     */
    ReservationResult reserve(OrderId id, List<OrderItem> items);

    /**
     * Releases a previously held stock reservation for the given order.
     *
     * <p>This method is called during order cancellation when the order has already
     * reached the {@code RESERVED} or {@code CONFIRMED} status. Implementations should
     * treat a release for an unknown or already-released reservation as a no-op.
     *
     * @param id    the order identifier whose reservation should be released; must not be
     *              {@code null}
     * @param items the line items whose reserved quantities should be returned to stock;
     *              must not be {@code null}
     */
    void release(OrderId id, List<OrderItem> items);
}
