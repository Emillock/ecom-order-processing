package com.example.orderprocessing.infrastructure.persistence;

import com.example.orderprocessing.domain.model.IdempotencyKey;
import com.example.orderprocessing.domain.model.Money;
import com.example.orderprocessing.domain.model.Order;
import com.example.orderprocessing.domain.model.OrderBuilder;
import com.example.orderprocessing.domain.model.OrderId;
import com.example.orderprocessing.domain.model.OrderItem;
import com.example.orderprocessing.domain.model.OrderStatus;
import com.example.orderprocessing.domain.model.OrderStatusEvent;
import com.example.orderprocessing.domain.model.Sku;
import com.example.orderprocessing.domain.port.OrderQuery;
import com.example.orderprocessing.domain.port.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed implementation of the {@link OrderRepository} domain port.
 *
 * <p>Acts as an Adapter (GoF Structural) between the domain's persistence port and the
 * Spring Data JPA repositories. All mapping between domain objects and JPA entities is
 * performed within this class, keeping the domain layer free of JPA types (DIP, Req 14.3).
 *
 * <p>Transaction boundaries:
 * <ul>
 *   <li>{@link #save} upserts the {@link OrderJpaEntity} and its items in a single
 *       {@code @Transactional} operation.</li>
 *   <li>{@link #appendStatusEvent} persists an {@link OrderStatusEventJpaEntity} and
 *       must be called within the same transaction as the corresponding {@link #save}
 *       so that the status update and its audit record are committed atomically
 *       (Requirement 8.4).</li>
 *   <li>{@link #findById} and {@link #search} are read-only transactions.</li>
 * </ul>
 */
@Repository
public class JpaOrderRepository implements OrderRepository {

    private final OrderJpaEntityRepository orderRepo;
    private final OrderStatusEventJpaEntityRepository eventRepo;

    /**
     * Constructs a {@code JpaOrderRepository} with the required Spring Data repositories.
     *
     * @param orderRepo the Spring Data JPA repository for order entities
     * @param eventRepo the Spring Data JPA repository for status-event entities
     */
    public JpaOrderRepository(OrderJpaEntityRepository orderRepo,
                               OrderStatusEventJpaEntityRepository eventRepo) {
        this.orderRepo = orderRepo;
        this.eventRepo = eventRepo;
    }

    // -------------------------------------------------------------------------
    // OrderRepository implementation
    // -------------------------------------------------------------------------

    /**
     * Retrieves an order by its unique identifier, mapping the JPA entity back to the
     * domain {@link Order} aggregate.
     *
     * @param id the order identifier; must not be {@code null}
     * @return an {@link Optional} containing the domain order if found, or empty
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findById(OrderId id) {
        return orderRepo.findById(id.value())
                .map(this::toDomain);
    }

    /**
     * Upserts the order and all its line items in a single transaction.
     *
     * <p>If an entity with the same UUID already exists it is updated in-place;
     * otherwise a new row is inserted. The items collection is replaced entirely
     * (orphan removal handles deletions).
     *
     * @param order the domain order to persist; must not be {@code null}
     * @return the saved domain order (identical to the input for this implementation)
     */
    @Override
    @Transactional
    public Order save(Order order) {
        OrderJpaEntity entity = orderRepo.findById(order.getId().value())
                .orElseGet(OrderJpaEntity::new);

        mapToEntity(order, entity);

        // Replace items: clear existing and add fresh ones (orphanRemoval handles deletes)
        entity.getItems().clear();
        for (OrderItem item : order.getItems()) {
            OrderItemJpaEntity itemEntity = new OrderItemJpaEntity(
                    UUID.randomUUID(),
                    entity,
                    item.sku().value(),
                    item.quantity(),
                    item.unitPrice().amount(),
                    item.unitPrice().currency().getCurrencyCode());
            entity.getItems().add(itemEntity);
        }

        orderRepo.save(entity);
        return order;
    }

    /**
     * Appends a status-transition event to the audit log.
     *
     * <p>This method must be called within the same transaction as the corresponding
     * {@link #save} call so that the event and the updated order status are committed
     * atomically (Requirement 8.4).
     *
     * @param e the status event to append; must not be {@code null}
     */
    @Override
    @Transactional
    public void appendStatusEvent(OrderStatusEvent e) {
        OrderStatusEventJpaEntity entity = new OrderStatusEventJpaEntity(
                UUID.randomUUID(),
                e.orderId().value(),
                e.from() != null ? e.from().name() : null,
                e.to().name(),
                e.at(),
                e.actor(),
                e.reason());
        eventRepo.save(entity);
    }

    /**
     * Returns all status-transition events for the given order in ascending chronological order.
     *
     * @param id the order identifier; must not be {@code null}
     * @return an ordered list of {@link OrderStatusEvent} records; never {@code null}
     */
    @Override
    @Transactional(readOnly = true)
    public List<OrderStatusEvent> findEventsByOrderId(OrderId id) {
        return eventRepo.findByOrderIdOrderByAtAsc(id.value()).stream()
                .map(e -> new OrderStatusEvent(
                        new OrderId(e.getOrderId()),
                        e.getFromStatus() != null ? OrderStatus.valueOf(e.getFromStatus()) : null,
                        OrderStatus.valueOf(e.getToStatus()),
                        e.getAt(),
                        e.getActor(),
                        e.getReason()))
                .toList();
    }

    /**
     * Returns a paginated, filtered view of orders matching the supplied query criteria.
     *
     * <p>Null fields in {@code query} are treated as "match any". The {@code customerId}
     * field is not stored on the order entity in the current schema and is therefore
     * ignored in the query predicate.
     *
     * @param query    the filter criteria; must not be {@code null}
     * @param pageable pagination and sort parameters; must not be {@code null}
     * @return a page of matching domain orders; never {@code null}
     */
    @Override
    @Transactional(readOnly = true)
    public Page<Order> search(OrderQuery query, Pageable pageable) {
        String statusStr = query.status() != null ? query.status().name() : null;

        Page<OrderJpaEntity> entityPage = orderRepo.search(
                statusStr,
                query.from(),
                query.to(),
                pageable);

        List<Order> orders = new ArrayList<>(entityPage.getNumberOfElements());
        for (OrderJpaEntity entity : entityPage.getContent()) {
            orders.add(toDomain(entity));
        }
        return new PageImpl<>(orders, pageable, entityPage.getTotalElements());
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    /**
     * Maps all scalar fields from the domain {@link Order} onto the given JPA entity.
     * Does not touch the items collection (handled separately in {@link #save}).
     *
     * @param order  the source domain order
     * @param entity the target JPA entity to populate
     */
    private void mapToEntity(Order order, OrderJpaEntity entity) {
        entity.setId(order.getId().value());
        entity.setStatus(order.getStatus().name());

        Currency currency = order.getSubtotal().currency();
        entity.setCurrency(currency.getCurrencyCode());

        entity.setSubtotalAmount(order.getSubtotal().amount());
        entity.setDiscountTotalAmount(order.getDiscountTotal().amount());
        entity.setTaxTotalAmount(order.getTaxTotal().amount());
        entity.setShippingTotalAmount(order.getShippingTotal().amount());
        entity.setGrandTotalAmount(order.getGrandTotal().amount());

        entity.setIdempotencyKey(
                order.getIdempotencyKey().map(IdempotencyKey::value).orElse(null));
        entity.setCreatedAt(order.getCreatedAt());
        entity.setUpdatedAt(order.getUpdatedAt());
        entity.setFailureReason(order.getFailureReason().orElse(null));
    }

    /**
     * Reconstructs a domain {@link Order} from a fully-loaded {@link OrderJpaEntity}.
     *
     * @param entity the JPA entity to map; must not be {@code null}
     * @return the equivalent domain {@link Order}
     */
    private Order toDomain(OrderJpaEntity entity) {
        Currency currency = Currency.getInstance(entity.getCurrency());

        List<OrderItem> items = new ArrayList<>(entity.getItems().size());
        for (OrderItemJpaEntity itemEntity : entity.getItems()) {
            Currency itemCurrency = Currency.getInstance(itemEntity.getCurrency());
            OrderItem item = new OrderItem(
                    new Sku(itemEntity.getSku()),
                    itemEntity.getQuantity(),
                    new Money(itemEntity.getUnitPriceAmount(), itemCurrency));
            items.add(item);
        }

        OrderBuilder builder = new OrderBuilder()
                .id(new OrderId(entity.getId()))
                .items(items)
                .status(OrderStatus.valueOf(entity.getStatus()))
                .currency(currency)
                .subtotal(new Money(entity.getSubtotalAmount(), currency))
                .discountTotal(new Money(entity.getDiscountTotalAmount(), currency))
                .taxTotal(new Money(entity.getTaxTotalAmount(), currency))
                .shippingTotal(new Money(entity.getShippingTotalAmount(), currency))
                .grandTotal(new Money(entity.getGrandTotalAmount(), currency))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt());

        if (entity.getIdempotencyKey() != null) {
            builder.idempotencyKey(new IdempotencyKey(entity.getIdempotencyKey()));
        }
        if (entity.getFailureReason() != null) {
            builder.failureReason(entity.getFailureReason());
        }

        return builder.build();
    }
}
