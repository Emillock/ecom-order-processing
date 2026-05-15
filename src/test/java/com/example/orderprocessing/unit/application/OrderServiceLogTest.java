package com.example.orderprocessing.unit.application;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.orderprocessing.application.OrderProcessingPipeline;
import com.example.orderprocessing.application.OrderService;
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
import com.example.orderprocessing.domain.port.OrderRepository;
import com.example.orderprocessing.application.concurrency.OrderLockRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the structured-log appender contract in {@link OrderService}.
 *
 * <p>Uses Logback's {@link ListAppender} to capture log output and asserts that every
 * accepted state transition emits a log entry containing the required fields:
 * {@code orderId}, {@code fromStatus}, {@code toStatus}, {@code timestamp},
 * {@code correlationId}, and {@code actor} (Requirement 16.1).
 *
 * <p>All ports are mocked with Mockito so no real infrastructure is needed.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceLogTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private IdempotencyStore idempotencyStore;

    @Mock
    private InventoryPort inventoryPort;

    @Mock
    private OrderProcessingPipeline pipeline;

    private OrderService orderService;
    private ListAppender<ILoggingEvent> listAppender;
    private Logger orderServiceLogger;

    @BeforeEach
    void setUp() {
        OrderLockRegistry lockRegistry = new OrderLockRegistry();
        orderService = new OrderService(
                orderRepository, idempotencyStore, inventoryPort, pipeline, lockRegistry);

        // Attach a ListAppender to the OrderService logger to capture log output
        orderServiceLogger = (Logger) LoggerFactory.getLogger(OrderService.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        orderServiceLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        orderServiceLogger.detachAppender(listAppender);
        listAppender.stop();
    }

    // -------------------------------------------------------------------------
    // Helper factories
    // -------------------------------------------------------------------------

    private OrderItem sampleItem() {
        return new OrderItem(new Sku("SKU-001"), 2, Money.of("10.00", "USD"));
    }

    private Order buildOrder(OrderId id, OrderStatus status) {
        OrderBuilder builder = new OrderBuilder()
                .id(id)
                .item(sampleItem())
                .status(status);
        return builder.build();
    }

    // -------------------------------------------------------------------------
    // Tests for create() — transition log on successful pipeline run
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("create() emits a transition log entry containing orderId, fromStatus, toStatus, timestamp, and actor")
    void create_emitsTransitionLog_withRequiredFields() {
        // Arrange
        OrderId orderId = OrderId.generate();
        Order initialOrder = buildOrder(orderId, OrderStatus.CREATED);
        Order confirmedOrder = buildOrder(orderId, OrderStatus.CONFIRMED);

        when(idempotencyStore.findExisting(any())).thenReturn(Optional.empty());
        // First save returns initialOrder (CREATED), second save returns confirmedOrder (CONFIRMED)
        when(orderRepository.save(any(Order.class)))
                .thenReturn(initialOrder)
                .thenReturn(confirmedOrder);
        doNothing().when(orderRepository).appendStatusEvent(any(OrderStatusEvent.class));
        when(pipeline.run(any(Order.class))).thenReturn(confirmedOrder);

        // Act
        orderService.create(List.of(sampleItem()), "customer-1", "default", Optional.empty());

        // Assert — at least one log entry must contain all required fields
        List<ILoggingEvent> logs = listAppender.list;
        assertThat(logs).isNotEmpty();

        ILoggingEvent transitionLog = logs.stream()
                .filter(e -> e.getFormattedMessage().contains("order_transition"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No order_transition log entry found"));

        String message = transitionLog.getFormattedMessage();
        assertThat(message).contains("orderId=" + orderId.value().toString());
        assertThat(message).contains("fromStatus=CREATED");
        assertThat(message).contains("toStatus=CONFIRMED");
        assertThat(message).contains("timestamp=");
        assertThat(message).contains("actor=system");
    }

    @Test
    @DisplayName("create() transition log entry contains a correlationId field")
    void create_emitsTransitionLog_withCorrelationId() {
        // Arrange
        OrderId orderId = OrderId.generate();
        Order initialOrder = buildOrder(orderId, OrderStatus.CREATED);
        Order confirmedOrder = buildOrder(orderId, OrderStatus.CONFIRMED);

        when(idempotencyStore.findExisting(any())).thenReturn(Optional.empty());
        when(orderRepository.save(any(Order.class)))
                .thenReturn(initialOrder)
                .thenReturn(confirmedOrder);
        doNothing().when(orderRepository).appendStatusEvent(any(OrderStatusEvent.class));
        when(pipeline.run(any(Order.class))).thenReturn(confirmedOrder);

        // Act
        orderService.create(List.of(sampleItem()), "customer-1", "default", Optional.empty());

        // Assert — correlationId field must be present
        ILoggingEvent transitionLog = listAppender.list.stream()
                .filter(e -> e.getFormattedMessage().contains("order_transition"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No order_transition log entry found"));

        assertThat(transitionLog.getFormattedMessage()).contains("correlationId=");
    }

    @Test
    @DisplayName("create() does NOT emit a transition log when the pipeline returns CREATED (no transition occurred)")
    void create_doesNotEmitTransitionLog_whenStatusRemainsCreated() {
        // Arrange — pipeline returns the order still in CREATED status (edge case)
        OrderId orderId = OrderId.generate();
        Order initialOrder = buildOrder(orderId, OrderStatus.CREATED);

        when(idempotencyStore.findExisting(any())).thenReturn(Optional.empty());
        when(orderRepository.save(any(Order.class))).thenReturn(initialOrder);
        doNothing().when(orderRepository).appendStatusEvent(any(OrderStatusEvent.class));
        when(pipeline.run(any(Order.class))).thenReturn(initialOrder);

        // Act
        orderService.create(List.of(sampleItem()), "customer-1", "default", Optional.empty());

        // Assert — no transition log should be emitted when status stays CREATED
        long transitionLogCount = listAppender.list.stream()
                .filter(e -> e.getFormattedMessage().contains("order_transition"))
                .count();
        assertThat(transitionLogCount).isZero();
    }

    @Test
    @DisplayName("create() emits transition log with toStatus=FAILED when pipeline returns a failed order")
    void create_emitsTransitionLog_withFailedStatus() {
        // Arrange
        OrderId orderId = OrderId.generate();
        Order initialOrder = buildOrder(orderId, OrderStatus.CREATED);
        Order failedOrder = buildOrder(orderId, OrderStatus.CREATED).withFailure("validation_failed:test");

        when(idempotencyStore.findExisting(any())).thenReturn(Optional.empty());
        when(orderRepository.save(any(Order.class)))
                .thenReturn(initialOrder)
                .thenReturn(failedOrder);
        doNothing().when(orderRepository).appendStatusEvent(any(OrderStatusEvent.class));
        when(pipeline.run(any(Order.class))).thenReturn(failedOrder);

        // Act
        orderService.create(List.of(sampleItem()), "customer-1", "default", Optional.empty());

        // Assert
        ILoggingEvent transitionLog = listAppender.list.stream()
                .filter(e -> e.getFormattedMessage().contains("order_transition"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No order_transition log entry found"));

        String message = transitionLog.getFormattedMessage();
        assertThat(message).contains("toStatus=FAILED");
        assertThat(message).contains("fromStatus=CREATED");
        assertThat(message).contains("orderId=" + orderId.value().toString());
        assertThat(message).contains("timestamp=");
        assertThat(message).contains("actor=system");
    }

    // -------------------------------------------------------------------------
    // Tests for cancel() — transition log on cancellation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("cancel() emits a transition log entry containing orderId, fromStatus=CREATED, toStatus=CANCELLED, timestamp, and actor")
    void cancel_emitsTransitionLog_withRequiredFields() {
        // Arrange
        OrderId orderId = OrderId.generate();
        Order createdOrder = buildOrder(orderId, OrderStatus.CREATED);
        Order cancelledOrder = buildOrder(orderId, OrderStatus.CANCELLED);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(createdOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(cancelledOrder);
        doNothing().when(orderRepository).appendStatusEvent(any(OrderStatusEvent.class));

        // Act
        orderService.cancel(orderId, "customer requested cancellation");

        // Assert
        List<ILoggingEvent> logs = listAppender.list;
        assertThat(logs).isNotEmpty();

        ILoggingEvent transitionLog = logs.stream()
                .filter(e -> e.getFormattedMessage().contains("order_transition"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No order_transition log entry found"));

        String message = transitionLog.getFormattedMessage();
        assertThat(message).contains("orderId=" + orderId.value().toString());
        assertThat(message).contains("fromStatus=CREATED");
        assertThat(message).contains("toStatus=CANCELLED");
        assertThat(message).contains("timestamp=");
        assertThat(message).contains("actor=system");
    }

    @Test
    @DisplayName("cancel() transition log entry contains a correlationId field")
    void cancel_emitsTransitionLog_withCorrelationId() {
        // Arrange
        OrderId orderId = OrderId.generate();
        Order createdOrder = buildOrder(orderId, OrderStatus.CREATED);
        Order cancelledOrder = buildOrder(orderId, OrderStatus.CANCELLED);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(createdOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(cancelledOrder);
        doNothing().when(orderRepository).appendStatusEvent(any(OrderStatusEvent.class));

        // Act
        orderService.cancel(orderId, "customer requested cancellation");

        // Assert
        ILoggingEvent transitionLog = listAppender.list.stream()
                .filter(e -> e.getFormattedMessage().contains("order_transition"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No order_transition log entry found"));

        assertThat(transitionLog.getFormattedMessage()).contains("correlationId=");
    }
}
