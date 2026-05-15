package com.example.orderprocessing.unit.application;

import com.example.orderprocessing.application.OrderProcessingPipeline;
import com.example.orderprocessing.application.OrderService;
import com.example.orderprocessing.application.concurrency.OrderLockRegistry;
import com.example.orderprocessing.domain.model.IdempotencyKey;
import com.example.orderprocessing.domain.model.Money;
import com.example.orderprocessing.domain.model.Order;
import com.example.orderprocessing.domain.model.OrderBuilder;
import com.example.orderprocessing.domain.model.OrderId;
import com.example.orderprocessing.domain.model.OrderItem;
import com.example.orderprocessing.domain.model.OrderStatus;
import com.example.orderprocessing.domain.model.OrderStatusEvent;
import com.example.orderprocessing.domain.model.Sku;
import com.example.orderprocessing.domain.port.IdempotencyStore;
import com.example.orderprocessing.domain.port.InventoryPort;
import com.example.orderprocessing.domain.port.OrderQuery;
import com.example.orderprocessing.domain.port.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OrderService} with all collaborators mocked.
 *
 * <p>Covers: idempotency hit, validation failure, pricing failure, reservation
 * success/failure, payment success/decline, cancel pre/post reserve, and
 * circuit-breaker-open → FAILED with dependency reason.
 *
 * <p>Satisfies Requirements 1.3, 2.3, 3.6, 4.2, 4.3, 4.5, 5.2, 5.3, 5.5, 7.1, 7.2.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private IdempotencyStore idempotencyStore;

    @Mock
    private InventoryPort inventoryPort;

    @Mock
    private OrderProcessingPipeline pipeline;

    private OrderLockRegistry lockRegistry;

    private OrderService orderService;

    // -------------------------------------------------------------------------
    // Shared test fixtures
    // -------------------------------------------------------------------------

    private static final Currency USD = Currency.getInstance("USD");

    private static final List<OrderItem> ITEMS = List.of(
            new OrderItem(new Sku("SKU-001"), 2, new Money(new BigDecimal("10.00"), USD)));

    private static final String CUSTOMER_ID = "customer-123";
    private static final String PRICING_PROFILE = "default";

    @BeforeEach
    void setUp() {
        // Use a real OrderLockRegistry — it has no external dependencies
        lockRegistry = new OrderLockRegistry();
        orderService = new OrderService(
                orderRepository, idempotencyStore, inventoryPort, pipeline, lockRegistry);
    }

    // -------------------------------------------------------------------------
    // Helper: build a minimal Order in a given status
    // -------------------------------------------------------------------------

    private Order buildOrder(OrderStatus status) {
        OrderBuilder builder = new OrderBuilder()
                .id(OrderId.generate())
                .items(ITEMS);
        if (status != OrderStatus.CREATED) {
            builder.status(status);
        }
        return builder.build();
    }

    private Order buildOrderWithId(OrderId id, OrderStatus status) {
        OrderBuilder builder = new OrderBuilder()
                .id(id)
                .items(ITEMS);
        if (status != OrderStatus.CREATED) {
            builder.status(status);
        }
        return builder.build();
    }

    // =========================================================================
    // create() — idempotency
    // =========================================================================

    @Test
    @DisplayName("Idempotency hit: when key already registered, returns existing order without running pipeline")
    void create_idempotencyHit_returnsExistingOrderWithoutPipeline() {
        // Arrange
        IdempotencyKey key = new IdempotencyKey("idem-key-1");
        OrderId existingId = OrderId.generate();
        Order existingOrder = buildOrderWithId(existingId, OrderStatus.CONFIRMED);

        when(idempotencyStore.findExisting(key)).thenReturn(Optional.of(existingId));
        when(orderRepository.findById(existingId)).thenReturn(Optional.of(existingOrder));

        // Act
        Order result = orderService.create(ITEMS, CUSTOMER_ID, PRICING_PROFILE, Optional.of(key));

        // Assert
        assertThat(result).isSameAs(existingOrder);
        verify(pipeline, never()).run(any());
        verify(orderRepository, never()).save(any());
    }

    // =========================================================================
    // create() — validation failure → FAILED
    // =========================================================================

    @Test
    @DisplayName("Validation failure: pipeline returns FAILED order, service persists and returns it")
    void create_validationFailure_persistsAndReturnsFailedOrder() {
        // Arrange
        Order initialOrder = buildOrder(OrderStatus.CREATED);
        Order failedOrder = initialOrder.withFailure("validation_failed:non_empty_items:items must not be empty");

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pipeline.run(any(Order.class))).thenReturn(failedOrder);

        // Act
        Order result = orderService.create(ITEMS, CUSTOMER_ID, PRICING_PROFILE, Optional.empty());

        // Assert
        assertThat(result.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(result.getFailureReason()).isPresent();
        // create() saves twice: once for the initial CREATED order, once for the final state
        verify(orderRepository, times(2)).save(any(Order.class));
    }

    // =========================================================================
    // create() — pricing failure → FAILED
    // =========================================================================

    @Test
    @DisplayName("Pricing failure: pipeline returns FAILED order, service persists and returns it")
    void create_pricingFailure_persistsAndReturnsFailedOrder() {
        // Arrange
        Order initialOrder = buildOrder(OrderStatus.CREATED);
        Order failedOrder = initialOrder.withFailure("pricing_failed:unknown profile");

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pipeline.run(any(Order.class))).thenReturn(failedOrder);

        // Act
        Order result = orderService.create(ITEMS, CUSTOMER_ID, PRICING_PROFILE, Optional.empty());

        // Assert
        assertThat(result.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(result.getFailureReason()).hasValueSatisfying(r -> assertThat(r).contains("pricing_failed"));
        // create() saves twice: once for the initial CREATED order, once for the final state
        verify(orderRepository, times(2)).save(any(Order.class));
    }

    // =========================================================================
    // create() — reservation success → RESERVED
    // =========================================================================

    @Test
    @DisplayName("Reservation success: pipeline returns RESERVED order, service persists and returns it")
    void create_reservationSuccess_persistsAndReturnsReservedOrder() {
        // Arrange
        Order initialOrder = buildOrder(OrderStatus.CREATED);
        Order reservedOrder = initialOrder.withStatus(OrderStatus.RESERVED);

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pipeline.run(any(Order.class))).thenReturn(reservedOrder);

        // Act
        Order result = orderService.create(ITEMS, CUSTOMER_ID, PRICING_PROFILE, Optional.empty());

        // Assert
        assertThat(result.getStatus()).isEqualTo(OrderStatus.RESERVED);
        // create() saves twice: once for the initial CREATED order, once for the final state
        verify(orderRepository, times(2)).save(any(Order.class));
    }

    // =========================================================================
    // create() — reservation failure (out-of-stock) → FAILED
    // =========================================================================

    @Test
    @DisplayName("Reservation failure (out-of-stock): pipeline returns FAILED order")
    void create_reservationOutOfStock_persistsAndReturnsFailedOrder() {
        // Arrange
        Order initialOrder = buildOrder(OrderStatus.CREATED);
        Order failedOrder = initialOrder.withFailure("inventory_out_of_stock:[SKU-001]");

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pipeline.run(any(Order.class))).thenReturn(failedOrder);

        // Act
        Order result = orderService.create(ITEMS, CUSTOMER_ID, PRICING_PROFILE, Optional.empty());

        // Assert
        assertThat(result.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(result.getFailureReason()).hasValueSatisfying(r -> assertThat(r).contains("inventory_out_of_stock"));
    }

    // =========================================================================
    // create() — payment success → CONFIRMED
    // =========================================================================

    @Test
    @DisplayName("Payment success: pipeline returns CONFIRMED order, service persists and returns it")
    void create_paymentSuccess_persistsAndReturnsConfirmedOrder() {
        // Arrange
        Order initialOrder = buildOrder(OrderStatus.CREATED);
        Order confirmedOrder = initialOrder.withStatus(OrderStatus.CONFIRMED);

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pipeline.run(any(Order.class))).thenReturn(confirmedOrder);

        // Act
        Order result = orderService.create(ITEMS, CUSTOMER_ID, PRICING_PROFILE, Optional.empty());

        // Assert
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        // create() saves twice: once for the initial CREATED order, once for the final state
        verify(orderRepository, times(2)).save(any(Order.class));
    }

    // =========================================================================
    // create() — payment decline → FAILED
    // =========================================================================

    @Test
    @DisplayName("Payment decline: pipeline returns FAILED order with payment_declined reason")
    void create_paymentDecline_persistsAndReturnsFailedOrder() {
        // Arrange
        Order initialOrder = buildOrder(OrderStatus.CREATED);
        Order failedOrder = initialOrder.withFailure("payment_declined:insufficient_funds");

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pipeline.run(any(Order.class))).thenReturn(failedOrder);

        // Act
        Order result = orderService.create(ITEMS, CUSTOMER_ID, PRICING_PROFILE, Optional.empty());

        // Assert
        assertThat(result.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(result.getFailureReason()).hasValueSatisfying(r -> assertThat(r).contains("payment_declined"));
    }

    // =========================================================================
    // cancel() — pre-reserve (CREATED/VALIDATED/PRICED): no inventory release
    // =========================================================================

    @Test
    @DisplayName("Cancel pre-reserve (CREATED): transitions to CANCELLED, no inventory release called")
    void cancel_preReserveCreated_cancelledWithoutInventoryRelease() {
        // Arrange
        OrderId orderId = OrderId.generate();
        Order createdOrder = buildOrderWithId(orderId, OrderStatus.CREATED);
        Order cancelledOrder = createdOrder.withStatus(OrderStatus.CANCELLED);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(createdOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(cancelledOrder);

        // Act
        Order result = orderService.cancel(orderId, "customer request");

        // Assert
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventoryPort, never()).release(any(), any());
    }

    @Test
    @DisplayName("Cancel pre-reserve (VALIDATED): transitions to CANCELLED, no inventory release called")
    void cancel_preReserveValidated_cancelledWithoutInventoryRelease() {
        // Arrange
        OrderId orderId = OrderId.generate();
        Order validatedOrder = buildOrderWithId(orderId, OrderStatus.VALIDATED);
        Order cancelledOrder = validatedOrder.withStatus(OrderStatus.CANCELLED);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(validatedOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(cancelledOrder);

        // Act
        Order result = orderService.cancel(orderId, "customer request");

        // Assert
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventoryPort, never()).release(any(), any());
    }

    @Test
    @DisplayName("Cancel pre-reserve (PRICED): transitions to CANCELLED, no inventory release called")
    void cancel_preReservePriced_cancelledWithoutInventoryRelease() {
        // Arrange
        OrderId orderId = OrderId.generate();
        Order pricedOrder = buildOrderWithId(orderId, OrderStatus.PRICED);
        Order cancelledOrder = pricedOrder.withStatus(OrderStatus.CANCELLED);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(pricedOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(cancelledOrder);

        // Act
        Order result = orderService.cancel(orderId, "customer request");

        // Assert
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventoryPort, never()).release(any(), any());
    }

    // =========================================================================
    // cancel() — post-reserve (RESERVED/CONFIRMED): inventory release called
    // =========================================================================

    @Test
    @DisplayName("Cancel post-reserve (RESERVED): transitions to CANCELLED, inventory release called")
    void cancel_postReserveReserved_cancelledWithInventoryRelease() {
        // Arrange
        OrderId orderId = OrderId.generate();
        Order reservedOrder = buildOrderWithId(orderId, OrderStatus.RESERVED);
        Order cancelledOrder = reservedOrder.withStatus(OrderStatus.CANCELLED);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(reservedOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(cancelledOrder);

        // Act
        Order result = orderService.cancel(orderId, "customer request");

        // Assert
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventoryPort).release(eq(orderId), any());
    }

    @Test
    @DisplayName("Cancel post-reserve (CONFIRMED): transitions to CANCELLED, inventory release called")
    void cancel_postReserveConfirmed_cancelledWithInventoryRelease() {
        // Arrange
        OrderId orderId = OrderId.generate();
        Order confirmedOrder = buildOrderWithId(orderId, OrderStatus.CONFIRMED);
        Order cancelledOrder = confirmedOrder.withStatus(OrderStatus.CANCELLED);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(confirmedOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(cancelledOrder);

        // Act
        Order result = orderService.cancel(orderId, "customer request");

        // Assert
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventoryPort).release(eq(orderId), any());
    }

    // =========================================================================
    // create() — circuit breaker open → FAILED with dependency reason
    // =========================================================================

    @Test
    @DisplayName("Circuit breaker open: pipeline returns FAILED order with dependency_unavailable reason")
    void create_circuitBreakerOpen_persistsFailedOrderWithDependencyReason() {
        // Arrange
        Order initialOrder = buildOrder(OrderStatus.CREATED);
        Order failedOrder = initialOrder.withFailure("dependency_unavailable:inventory");

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pipeline.run(any(Order.class))).thenReturn(failedOrder);

        // Act
        Order result = orderService.create(ITEMS, CUSTOMER_ID, PRICING_PROFILE, Optional.empty());

        // Assert
        assertThat(result.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(result.getFailureReason()).hasValueSatisfying(r ->
                assertThat(r).contains("dependency_unavailable"));
    }

    // =========================================================================
    // get() — delegates to repository
    // =========================================================================

    @Test
    @DisplayName("get: returns order when found in repository")
    void get_orderFound_returnsOrder() {
        // Arrange
        OrderId orderId = OrderId.generate();
        Order order = buildOrderWithId(orderId, OrderStatus.CONFIRMED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        // Act
        Order result = orderService.get(orderId);

        // Assert
        assertThat(result).isSameAs(order);
    }

    @Test
    @DisplayName("get: throws IllegalStateException when order not found")
    void get_orderNotFound_throwsIllegalStateException() {
        // Arrange
        OrderId orderId = OrderId.generate();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> orderService.get(orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Order not found");
    }

    // =========================================================================
    // list() — delegates to repository
    // =========================================================================

    @Test
    @DisplayName("list: returns page of orders from repository")
    void list_returnsPageFromRepository() {
        // Arrange
        Order order = buildOrder(OrderStatus.CREATED);
        Page<Order> page = new PageImpl<>(List.of(order));
        OrderQuery query = new OrderQuery(null, null, null, null);
        Pageable pageable = PageRequest.of(0, 20);
        when(orderRepository.search(query, pageable)).thenReturn(page);

        // Act
        Page<Order> result = orderService.list(query, pageable);

        // Assert
        assertThat(result.getContent()).hasSize(1);
    }

    // =========================================================================
    // events() — delegates to repository
    // =========================================================================

    @Test
    @DisplayName("events: returns event list when order exists")
    void events_orderExists_returnsEvents() {
        // Arrange
        OrderId orderId = OrderId.generate();
        Order order = buildOrderWithId(orderId, OrderStatus.CONFIRMED);
        OrderStatusEvent event = new OrderStatusEvent(
                orderId, null, OrderStatus.CREATED,
                java.time.Instant.now(), "system", null);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.findEventsByOrderId(orderId)).thenReturn(List.of(event));

        // Act
        List<OrderStatusEvent> result = orderService.events(orderId);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).to()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    @DisplayName("events: throws IllegalStateException when order not found")
    void events_orderNotFound_throwsIllegalStateException() {
        // Arrange
        OrderId orderId = OrderId.generate();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> orderService.events(orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Order not found");
    }

    // =========================================================================
    // cancel() — order not found
    // =========================================================================

    @Test
    @DisplayName("cancel: throws IllegalStateException when order not found")
    void cancel_orderNotFound_throwsIllegalStateException() {
        // Arrange
        OrderId orderId = OrderId.generate();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> orderService.cancel(orderId, "reason"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Order not found");
    }

    // =========================================================================
    // cancel() — terminal state (FAILED) → throws
    // =========================================================================

    @Test
    @DisplayName("cancel: throws when order is in terminal FAILED state (invalid transition)")
    void cancel_terminalFailedOrder_throwsIllegalStateException() {
        // Arrange
        OrderId orderId = OrderId.generate();
        Order failedOrder = buildOrderWithId(orderId, OrderStatus.CREATED)
                .withFailure("some reason");

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(failedOrder));

        // Act & Assert
        assertThatThrownBy(() -> orderService.cancel(orderId, "reason"))
                .isInstanceOf(IllegalStateException.class);
    }

    // =========================================================================
    // create() — idempotency key registered after successful creation
    // =========================================================================

    @Test
    @DisplayName("create with idempotency key: registers key after successful pipeline run")
    void create_withIdempotencyKey_registersKeyAfterSuccess() {
        // Arrange
        IdempotencyKey key = new IdempotencyKey("new-key-xyz");
        Order confirmedOrder = buildOrder(OrderStatus.CREATED).withStatus(OrderStatus.CONFIRMED);

        when(idempotencyStore.findExisting(key)).thenReturn(Optional.empty());
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pipeline.run(any(Order.class))).thenReturn(confirmedOrder);

        // Act
        Order result = orderService.create(ITEMS, CUSTOMER_ID, PRICING_PROFILE, Optional.of(key));

        // Assert
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(idempotencyStore).register(eq(key), any(OrderId.class));
    }
}
