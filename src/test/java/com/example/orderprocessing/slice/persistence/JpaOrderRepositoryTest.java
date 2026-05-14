package com.example.orderprocessing.slice.persistence;

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
import com.example.orderprocessing.infrastructure.persistence.JpaOrderRepository;
import com.example.orderprocessing.infrastructure.persistence.OrderJpaEntityRepository;
import com.example.orderprocessing.infrastructure.persistence.OrderStatusEventJpaEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @DataJpaTest} slice tests for {@link JpaOrderRepository}.
 *
 * <p>Uses H2 in-memory database (configured in {@code application-test.yml}) with
 * {@code create-drop} DDL so each test run starts with a clean schema.
 * Tests cover upsert (save new + save existing), status-event append, and
 * paginated search by status and time range (Requirements 1.4, 6.3, 8.4, 9.1).
 */
@DataJpaTest
@ActiveProfiles("test")
class JpaOrderRepositoryTest {

    @Autowired
    private OrderJpaEntityRepository orderJpaEntityRepository;

    @Autowired
    private OrderStatusEventJpaEntityRepository eventJpaEntityRepository;

    private JpaOrderRepository repository;

    private static final Currency USD = Currency.getInstance("USD");

    @BeforeEach
    void setUp() {
        repository = new JpaOrderRepository(orderJpaEntityRepository, eventJpaEntityRepository);
    }

    // -------------------------------------------------------------------------
    // Helper factory
    // -------------------------------------------------------------------------

    /** Builds a minimal valid {@link Order} with a random ID and one item. */
    private Order buildOrder(OrderId id, OrderStatus status) {
        Money unitPrice = new Money(new BigDecimal("10.00"), USD);
        OrderItem item = new OrderItem(new Sku("SKU-001"), 2, unitPrice);
        Money subtotal = new Money(new BigDecimal("20.00"), USD);
        Money zero = Money.zero(USD);
        return new OrderBuilder()
                .id(id)
                .item(item)
                .status(status)
                .currency(USD)
                .subtotal(subtotal)
                .discountTotal(zero)
                .taxTotal(zero)
                .shippingTotal(zero)
                .grandTotal(subtotal)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // -------------------------------------------------------------------------
    // findById — miss
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findById returns empty when order does not exist")
    void findById_miss_returnsEmpty() {
        Optional<Order> result = repository.findById(OrderId.generate());

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // save — insert new order
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("save inserts a new order and findById retrieves it")
    void save_newOrder_persistsAndRetrievable() {
        OrderId id = OrderId.generate();
        Order order = buildOrder(id, OrderStatus.CREATED);

        repository.save(order);

        Optional<Order> found = repository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(id);
        assertThat(found.get().getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(found.get().getItems()).hasSize(1);
        assertThat(found.get().getItems().get(0).sku().value()).isEqualTo("SKU-001");
    }

    // -------------------------------------------------------------------------
    // save — upsert existing order (status update)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("save updates an existing order when called with the same ID")
    void save_existingOrder_updatesStatus() {
        OrderId id = OrderId.generate();
        Order original = buildOrder(id, OrderStatus.CREATED);
        repository.save(original);

        Order updated = original.withStatus(OrderStatus.VALIDATED);
        repository.save(updated);

        Optional<Order> found = repository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(OrderStatus.VALIDATED);
    }

    @Test
    @DisplayName("save upsert replaces items when items change")
    void save_upsert_replacesItems() {
        OrderId id = OrderId.generate();
        Order original = buildOrder(id, OrderStatus.CREATED);
        repository.save(original);

        // Build an updated order with a different item
        Money unitPrice = new Money(new BigDecimal("5.00"), USD);
        OrderItem newItem = new OrderItem(new Sku("SKU-002"), 3, unitPrice);
        Money subtotal = new Money(new BigDecimal("15.00"), USD);
        Money zero = Money.zero(USD);
        Order withNewItem = new OrderBuilder()
                .id(id)
                .item(newItem)
                .status(OrderStatus.VALIDATED)
                .currency(USD)
                .subtotal(subtotal)
                .discountTotal(zero)
                .taxTotal(zero)
                .shippingTotal(zero)
                .grandTotal(subtotal)
                .createdAt(original.getCreatedAt())
                .updatedAt(Instant.now())
                .build();

        repository.save(withNewItem);

        Optional<Order> found = repository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getItems()).hasSize(1);
        assertThat(found.get().getItems().get(0).sku().value()).isEqualTo("SKU-002");
    }

    // -------------------------------------------------------------------------
    // save — order with idempotency key
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("save persists and retrieves idempotency key")
    void save_withIdempotencyKey_persistsKey() {
        OrderId id = OrderId.generate();
        Money unitPrice = new Money(new BigDecimal("10.00"), USD);
        OrderItem item = new OrderItem(new Sku("SKU-001"), 1, unitPrice);
        Money subtotal = new Money(new BigDecimal("10.00"), USD);
        Money zero = Money.zero(USD);
        Order order = new OrderBuilder()
                .id(id)
                .item(item)
                .status(OrderStatus.CREATED)
                .currency(USD)
                .subtotal(subtotal)
                .discountTotal(zero)
                .taxTotal(zero)
                .shippingTotal(zero)
                .grandTotal(subtotal)
                .idempotencyKey(new IdempotencyKey("idem-key-abc"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        repository.save(order);

        Optional<Order> found = repository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getIdempotencyKey())
                .isPresent()
                .hasValueSatisfying(k -> assertThat(k.value()).isEqualTo("idem-key-abc"));
    }

    // -------------------------------------------------------------------------
    // appendStatusEvent
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("appendStatusEvent persists an event retrievable via the event repository")
    void appendStatusEvent_persistsEvent() {
        OrderId id = OrderId.generate();
        Order order = buildOrder(id, OrderStatus.CREATED);
        repository.save(order);

        OrderStatusEvent event = new OrderStatusEvent(
                id,
                OrderStatus.CREATED,
                OrderStatus.VALIDATED,
                Instant.now(),
                "system",
                null);

        repository.appendStatusEvent(event);

        var events = eventJpaEntityRepository.findByOrderIdOrderByAtAsc(id.value());
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getFromStatus()).isEqualTo("CREATED");
        assertThat(events.get(0).getToStatus()).isEqualTo("VALIDATED");
        assertThat(events.get(0).getActor()).isEqualTo("system");
        assertThat(events.get(0).getReason()).isNull();
    }

    @Test
    @DisplayName("appendStatusEvent with null fromStatus (initial creation event) is persisted correctly")
    void appendStatusEvent_nullFromStatus_persisted() {
        OrderId id = OrderId.generate();
        Order order = buildOrder(id, OrderStatus.CREATED);
        repository.save(order);

        OrderStatusEvent event = new OrderStatusEvent(
                id,
                null,
                OrderStatus.CREATED,
                Instant.now(),
                "system",
                null);

        repository.appendStatusEvent(event);

        var events = eventJpaEntityRepository.findByOrderIdOrderByAtAsc(id.value());
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getFromStatus()).isNull();
        assertThat(events.get(0).getToStatus()).isEqualTo("CREATED");
    }

    @Test
    @DisplayName("appendStatusEvent with reason persists the reason field")
    void appendStatusEvent_withReason_persistsReason() {
        OrderId id = OrderId.generate();
        Order order = buildOrder(id, OrderStatus.CREATED);
        repository.save(order);

        OrderStatusEvent event = new OrderStatusEvent(
                id,
                OrderStatus.CREATED,
                OrderStatus.CANCELLED,
                Instant.now(),
                "customer:42",
                "customer requested cancellation");

        repository.appendStatusEvent(event);

        var events = eventJpaEntityRepository.findByOrderIdOrderByAtAsc(id.value());
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getReason()).isEqualTo("customer requested cancellation");
        assertThat(events.get(0).getActor()).isEqualTo("customer:42");
    }

    @Test
    @DisplayName("appendStatusEvent appends multiple events in order")
    void appendStatusEvent_multipleEvents_appendedInOrder() {
        OrderId id = OrderId.generate();
        Order order = buildOrder(id, OrderStatus.CREATED);
        repository.save(order);

        Instant t1 = Instant.now().minusSeconds(10);
        Instant t2 = Instant.now().minusSeconds(5);
        Instant t3 = Instant.now();

        repository.appendStatusEvent(new OrderStatusEvent(id, null, OrderStatus.CREATED, t1, "system", null));
        repository.appendStatusEvent(new OrderStatusEvent(id, OrderStatus.CREATED, OrderStatus.VALIDATED, t2, "system", null));
        repository.appendStatusEvent(new OrderStatusEvent(id, OrderStatus.VALIDATED, OrderStatus.PRICED, t3, "system", null));

        var events = eventJpaEntityRepository.findByOrderIdOrderByAtAsc(id.value());
        assertThat(events).hasSize(3);
        assertThat(events.get(0).getToStatus()).isEqualTo("CREATED");
        assertThat(events.get(1).getToStatus()).isEqualTo("VALIDATED");
        assertThat(events.get(2).getToStatus()).isEqualTo("PRICED");
    }

    // -------------------------------------------------------------------------
    // search — by status
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("search by status returns only orders with matching status")
    void search_byStatus_returnsMatchingOrders() {
        OrderId id1 = OrderId.generate();
        OrderId id2 = OrderId.generate();
        OrderId id3 = OrderId.generate();

        repository.save(buildOrder(id1, OrderStatus.CREATED));
        repository.save(buildOrder(id2, OrderStatus.VALIDATED));
        repository.save(buildOrder(id3, OrderStatus.CREATED));

        OrderQuery query = new OrderQuery(OrderStatus.CREATED, null, null, null);
        Page<Order> result = repository.search(query, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .extracting(Order::getStatus)
                .containsOnly(OrderStatus.CREATED);
    }

    @Test
    @DisplayName("search with null status returns all orders")
    void search_nullStatus_returnsAllOrders() {
        repository.save(buildOrder(OrderId.generate(), OrderStatus.CREATED));
        repository.save(buildOrder(OrderId.generate(), OrderStatus.VALIDATED));
        repository.save(buildOrder(OrderId.generate(), OrderStatus.PRICED));

        OrderQuery query = new OrderQuery(null, null, null, null);
        Page<Order> result = repository.search(query, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(3);
    }

    // -------------------------------------------------------------------------
    // search — by time range
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("search by time range returns only orders within the range")
    void search_byTimeRange_returnsOrdersInRange() {
        Instant past = Instant.now().minus(2, ChronoUnit.HOURS);
        Instant recent = Instant.now().minus(30, ChronoUnit.MINUTES);
        Instant future = Instant.now().plus(1, ChronoUnit.HOURS);

        // Order created 2 hours ago
        OrderId oldId = OrderId.generate();
        Money unitPrice = new Money(new BigDecimal("10.00"), USD);
        OrderItem item = new OrderItem(new Sku("SKU-001"), 1, unitPrice);
        Money subtotal = new Money(new BigDecimal("10.00"), USD);
        Money zero = Money.zero(USD);
        Order oldOrder = new OrderBuilder()
                .id(oldId)
                .item(item)
                .status(OrderStatus.CREATED)
                .currency(USD)
                .subtotal(subtotal)
                .discountTotal(zero)
                .taxTotal(zero)
                .shippingTotal(zero)
                .grandTotal(subtotal)
                .createdAt(past)
                .updatedAt(past)
                .build();
        repository.save(oldOrder);

        // Order created 30 minutes ago
        OrderId recentId = OrderId.generate();
        Order recentOrder = new OrderBuilder()
                .id(recentId)
                .item(item)
                .status(OrderStatus.CREATED)
                .currency(USD)
                .subtotal(subtotal)
                .discountTotal(zero)
                .taxTotal(zero)
                .shippingTotal(zero)
                .grandTotal(subtotal)
                .createdAt(recent)
                .updatedAt(recent)
                .build();
        repository.save(recentOrder);

        // Search for orders created in the last hour
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        OrderQuery query = new OrderQuery(null, null, oneHourAgo, future);
        Page<Order> result = repository.search(query, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(recentId);
    }

    @Test
    @DisplayName("search with both status and time range filters applies both predicates")
    void search_statusAndTimeRange_appliesBothFilters() {
        Instant now = Instant.now();
        Instant oneHourAgo = now.minus(1, ChronoUnit.HOURS);

        Money unitPrice = new Money(new BigDecimal("10.00"), USD);
        OrderItem item = new OrderItem(new Sku("SKU-001"), 1, unitPrice);
        Money subtotal = new Money(new BigDecimal("10.00"), USD);
        Money zero = Money.zero(USD);

        // CREATED order within range
        OrderId id1 = OrderId.generate();
        Order o1 = new OrderBuilder()
                .id(id1).item(item).status(OrderStatus.CREATED).currency(USD)
                .subtotal(subtotal).discountTotal(zero).taxTotal(zero).shippingTotal(zero).grandTotal(subtotal)
                .createdAt(now.minus(30, ChronoUnit.MINUTES)).updatedAt(now)
                .build();
        repository.save(o1);

        // VALIDATED order within range — should NOT match status filter
        OrderId id2 = OrderId.generate();
        Order o2 = new OrderBuilder()
                .id(id2).item(item).status(OrderStatus.VALIDATED).currency(USD)
                .subtotal(subtotal).discountTotal(zero).taxTotal(zero).shippingTotal(zero).grandTotal(subtotal)
                .createdAt(now.minus(30, ChronoUnit.MINUTES)).updatedAt(now)
                .build();
        repository.save(o2);

        // CREATED order outside range — should NOT match time filter
        OrderId id3 = OrderId.generate();
        Order o3 = new OrderBuilder()
                .id(id3).item(item).status(OrderStatus.CREATED).currency(USD)
                .subtotal(subtotal).discountTotal(zero).taxTotal(zero).shippingTotal(zero).grandTotal(subtotal)
                .createdAt(now.minus(2, ChronoUnit.HOURS)).updatedAt(now)
                .build();
        repository.save(o3);

        OrderQuery query = new OrderQuery(OrderStatus.CREATED, null, oneHourAgo, now.plus(1, ChronoUnit.HOURS));
        Page<Order> result = repository.search(query, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(id1);
    }

    // -------------------------------------------------------------------------
    // search — pagination
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("search respects page size and returns correct total count")
    void search_pagination_respectsPageSize() {
        for (int i = 0; i < 5; i++) {
            repository.save(buildOrder(OrderId.generate(), OrderStatus.CREATED));
        }

        OrderQuery query = new OrderQuery(OrderStatus.CREATED, null, null, null);
        Page<Order> page0 = repository.search(query, PageRequest.of(0, 2));
        Page<Order> page1 = repository.search(query, PageRequest.of(1, 2));

        assertThat(page0.getTotalElements()).isEqualTo(5);
        assertThat(page0.getContent()).hasSize(2);
        assertThat(page1.getContent()).hasSize(2);
    }

    // -------------------------------------------------------------------------
    // save — failure reason
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("save persists and retrieves failure reason for FAILED orders")
    void save_failedOrder_persistsFailureReason() {
        OrderId id = OrderId.generate();
        Order order = buildOrder(id, OrderStatus.CREATED);
        Order failed = order.withFailure("inventory_unavailable");
        repository.save(failed);

        Optional<Order> found = repository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(found.get().getFailureReason())
                .isPresent()
                .hasValue("inventory_unavailable");
    }
}
