package com.example.orderprocessing.unit.application.command;

import com.example.orderprocessing.application.OrderProcessingPipeline;
import com.example.orderprocessing.application.command.CreateOrderCommand;
import com.example.orderprocessing.application.concurrency.OrderLockRegistry;
import com.example.orderprocessing.domain.model.Money;
import com.example.orderprocessing.domain.model.Order;
import com.example.orderprocessing.domain.model.OrderBuilder;
import com.example.orderprocessing.domain.model.OrderId;
import com.example.orderprocessing.domain.model.OrderItem;
import com.example.orderprocessing.domain.model.OrderStatus;
import com.example.orderprocessing.domain.model.OrderStatusEvent;
import com.example.orderprocessing.domain.model.Sku;
import com.example.orderprocessing.domain.port.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CreateOrderCommand}.
 *
 * <p>Covers: successful pipeline run → CONFIRMED, pipeline failure → FAILED,
 * constructor argument validation, and audit event recording.
 *
 * <p>Validates: Requirements 6.3, 7.1, 11.3, 13.3
 */
@ExtendWith(MockitoExtension.class)
class CreateOrderCommandTest {

    @Mock
    private OrderProcessingPipeline pipeline;

    @Mock
    private OrderRepository repository;

    private OrderLockRegistry lockRegistry;

    private static final Currency USD = Currency.getInstance("USD");
    private static final List<OrderItem> ITEMS = List.of(
            new OrderItem(new Sku("SKU-001"), 1, new Money(new BigDecimal("15.00"), USD)));

    @BeforeEach
    void setUp() {
        lockRegistry = new OrderLockRegistry();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Order buildCreatedOrder() {
        return new OrderBuilder().id(OrderId.generate()).items(ITEMS).build();
    }

    // =========================================================================
    // Constructor argument validation
    // =========================================================================

    @Test
    @DisplayName("Constructor: throws when order is null")
    void constructor_nullOrder_throws() {
        assertThatThrownBy(() -> new CreateOrderCommand(null, pipeline, repository, lockRegistry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("order");
    }

    @Test
    @DisplayName("Constructor: throws when pipeline is null")
    void constructor_nullPipeline_throws() {
        assertThatThrownBy(() -> new CreateOrderCommand(buildCreatedOrder(), null, repository, lockRegistry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pipeline");
    }

    @Test
    @DisplayName("Constructor: throws when repository is null")
    void constructor_nullRepository_throws() {
        assertThatThrownBy(() -> new CreateOrderCommand(buildCreatedOrder(), pipeline, null, lockRegistry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repository");
    }

    @Test
    @DisplayName("Constructor: throws when lockRegistry is null")
    void constructor_nullLockRegistry_throws() {
        assertThatThrownBy(() -> new CreateOrderCommand(buildCreatedOrder(), pipeline, repository, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lockRegistry");
    }

    // =========================================================================
    // execute() — successful pipeline run
    // =========================================================================

    @Test
    @DisplayName("execute: pipeline returns CONFIRMED order, command saves and returns it")
    void execute_pipelineReturnsConfirmed_savesAndReturnsConfirmedOrder() {
        Order created = buildCreatedOrder();
        Order confirmed = created.withStatus(OrderStatus.CONFIRMED);

        when(repository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pipeline.run(any(Order.class))).thenReturn(confirmed);

        CreateOrderCommand cmd = new CreateOrderCommand(created, pipeline, repository, lockRegistry);
        Order result = cmd.execute();

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("execute: saves the order twice (initial CREATED + final state)")
    void execute_savesTwice() {
        Order created = buildCreatedOrder();
        Order confirmed = created.withStatus(OrderStatus.CONFIRMED);

        when(repository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pipeline.run(any(Order.class))).thenReturn(confirmed);

        new CreateOrderCommand(created, pipeline, repository, lockRegistry).execute();

        verify(repository, times(2)).save(any(Order.class));
    }

    @Test
    @DisplayName("execute: appends at least two status events (CREATED + final)")
    void execute_appendsStatusEvents() {
        Order created = buildCreatedOrder();
        Order confirmed = created.withStatus(OrderStatus.CONFIRMED);

        when(repository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pipeline.run(any(Order.class))).thenReturn(confirmed);

        new CreateOrderCommand(created, pipeline, repository, lockRegistry).execute();

        verify(repository, atLeast(2)).appendStatusEvent(any(OrderStatusEvent.class));
    }

    @Test
    @DisplayName("execute: first status event records CREATED transition (null from)")
    void execute_firstEventRecordsCreatedTransition() {
        Order created = buildCreatedOrder();
        Order confirmed = created.withStatus(OrderStatus.CONFIRMED);

        when(repository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pipeline.run(any(Order.class))).thenReturn(confirmed);

        new CreateOrderCommand(created, pipeline, repository, lockRegistry).execute();

        ArgumentCaptor<OrderStatusEvent> eventCaptor = ArgumentCaptor.forClass(OrderStatusEvent.class);
        verify(repository, atLeast(1)).appendStatusEvent(eventCaptor.capture());

        // First event should be the CREATED event (from = null)
        List<OrderStatusEvent> events = eventCaptor.getAllValues();
        assertThat(events).anyMatch(e -> e.to() == OrderStatus.CREATED && e.from() == null);
    }

    // =========================================================================
    // execute() — pipeline failure → FAILED
    // =========================================================================

    @Test
    @DisplayName("execute: pipeline returns FAILED order, command saves and returns it")
    void execute_pipelineReturnsFailed_savesAndReturnsFailedOrder() {
        Order created = buildCreatedOrder();
        Order failed = created.withFailure("validation_failed:NON_EMPTY_ITEMS:items empty");

        when(repository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pipeline.run(any(Order.class))).thenReturn(failed);

        CreateOrderCommand cmd = new CreateOrderCommand(created, pipeline, repository, lockRegistry);
        Order result = cmd.execute();

        assertThat(result.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(result.getFailureReason()).isPresent();
    }

    @Test
    @DisplayName("execute: final event records FAILED transition when pipeline fails")
    void execute_pipelineFails_finalEventRecordsFailedTransition() {
        Order created = buildCreatedOrder();
        Order failed = created.withFailure("pricing_failed:unknown profile");

        when(repository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pipeline.run(any(Order.class))).thenReturn(failed);

        new CreateOrderCommand(created, pipeline, repository, lockRegistry).execute();

        ArgumentCaptor<OrderStatusEvent> eventCaptor = ArgumentCaptor.forClass(OrderStatusEvent.class);
        verify(repository, atLeast(1)).appendStatusEvent(eventCaptor.capture());

        List<OrderStatusEvent> events = eventCaptor.getAllValues();
        assertThat(events).anyMatch(e -> e.to() == OrderStatus.FAILED);
    }
}
