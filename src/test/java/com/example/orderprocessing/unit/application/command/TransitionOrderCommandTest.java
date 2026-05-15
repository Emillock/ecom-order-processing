package com.example.orderprocessing.unit.application.command;

import com.example.orderprocessing.application.command.TransitionOrderCommand;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TransitionOrderCommand}.
 *
 * <p>Covers: valid transitions, invalid transitions (terminal states), order not found,
 * constructor argument validation, and audit event recording.
 *
 * <p>Validates: Requirements 6.1, 6.3, 7.3, 11.3, 13.3
 */
@ExtendWith(MockitoExtension.class)
class TransitionOrderCommandTest {

    @Mock
    private OrderRepository repository;

    private OrderLockRegistry lockRegistry;

    private static final Currency USD = Currency.getInstance("USD");
    private static final List<OrderItem> ITEMS = List.of(
            new OrderItem(new Sku("SKU-001"), 1, new Money(new BigDecimal("20.00"), USD)));

    @BeforeEach
    void setUp() {
        lockRegistry = new OrderLockRegistry();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Order buildOrder(OrderId id, OrderStatus status) {
        OrderBuilder builder = new OrderBuilder().id(id).items(ITEMS);
        if (status != OrderStatus.CREATED) {
            builder.status(status);
        }
        return builder.build();
    }

    // =========================================================================
    // Constructor argument validation
    // =========================================================================

    @Test
    @DisplayName("Constructor: throws when orderId is null")
    void constructor_nullOrderId_throws() {
        assertThatThrownBy(() -> new TransitionOrderCommand(
                null, OrderStatus.VALIDATED, repository, lockRegistry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("orderId");
    }

    @Test
    @DisplayName("Constructor: throws when targetStatus is null")
    void constructor_nullTargetStatus_throws() {
        assertThatThrownBy(() -> new TransitionOrderCommand(
                OrderId.generate(), null, repository, lockRegistry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetStatus");
    }

    @Test
    @DisplayName("Constructor: throws when repository is null")
    void constructor_nullRepository_throws() {
        assertThatThrownBy(() -> new TransitionOrderCommand(
                OrderId.generate(), OrderStatus.VALIDATED, null, lockRegistry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repository");
    }

    @Test
    @DisplayName("Constructor: throws when lockRegistry is null")
    void constructor_nullLockRegistry_throws() {
        assertThatThrownBy(() -> new TransitionOrderCommand(
                OrderId.generate(), OrderStatus.VALIDATED, repository, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lockRegistry");
    }

    @Test
    @DisplayName("Constructor (full): throws when actor is null")
    void constructor_full_nullActor_throws() {
        assertThatThrownBy(() -> new TransitionOrderCommand(
                OrderId.generate(), OrderStatus.VALIDATED, null, null, repository, lockRegistry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actor");
    }

    @Test
    @DisplayName("Constructor (full): throws when actor is blank")
    void constructor_full_blankActor_throws() {
        assertThatThrownBy(() -> new TransitionOrderCommand(
                OrderId.generate(), OrderStatus.VALIDATED, "  ", null, repository, lockRegistry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actor");
    }

    @Test
    @DisplayName("Getters return the values supplied at construction")
    void getters_returnConstructorValues() {
        OrderId orderId = OrderId.generate();
        TransitionOrderCommand cmd = new TransitionOrderCommand(
                orderId, OrderStatus.SHIPPED, "operator:42", "manual advance", repository, lockRegistry);

        assertThat(cmd.getOrderId()).isEqualTo(orderId);
        assertThat(cmd.getTargetStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(cmd.getActor()).isEqualTo("operator:42");
        assertThat(cmd.getReason()).isEqualTo("manual advance");
    }

    @Test
    @DisplayName("Default constructor uses 'system' actor and null reason")
    void defaultConstructor_usesSystemActorAndNullReason() {
        OrderId orderId = OrderId.generate();
        TransitionOrderCommand cmd = new TransitionOrderCommand(
                orderId, OrderStatus.VALIDATED, repository, lockRegistry);

        assertThat(cmd.getActor()).isEqualTo("system");
        assertThat(cmd.getReason()).isNull();
    }

    // =========================================================================
    // execute() — valid transitions
    // =========================================================================

    @Test
    @DisplayName("CREATED → VALIDATED: transitions successfully and saves the order")
    void execute_createdToValidated_savesTransitionedOrder() {
        OrderId orderId = OrderId.generate();
        Order created = buildOrder(orderId, OrderStatus.CREATED);
        Order validated = created.withStatus(OrderStatus.VALIDATED);

        when(repository.findById(orderId)).thenReturn(Optional.of(created));
        when(repository.save(any(Order.class))).thenReturn(validated);

        TransitionOrderCommand cmd = new TransitionOrderCommand(
                orderId, OrderStatus.VALIDATED, repository, lockRegistry);
        Order result = cmd.execute();

        assertThat(result.getStatus()).isEqualTo(OrderStatus.VALIDATED);
        verify(repository).save(any(Order.class));
    }

    @Test
    @DisplayName("CONFIRMED → SHIPPED: transitions successfully")
    void execute_confirmedToShipped_savesTransitionedOrder() {
        OrderId orderId = OrderId.generate();
        Order confirmed = buildOrder(orderId, OrderStatus.CONFIRMED);
        Order shipped = confirmed.withStatus(OrderStatus.SHIPPED);

        when(repository.findById(orderId)).thenReturn(Optional.of(confirmed));
        when(repository.save(any(Order.class))).thenReturn(shipped);

        TransitionOrderCommand cmd = new TransitionOrderCommand(
                orderId, OrderStatus.SHIPPED, repository, lockRegistry);
        Order result = cmd.execute();

        assertThat(result.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    @DisplayName("SHIPPED → DELIVERED: transitions successfully")
    void execute_shippedToDelivered_savesTransitionedOrder() {
        OrderId orderId = OrderId.generate();
        Order shipped = buildOrder(orderId, OrderStatus.SHIPPED);
        Order delivered = shipped.withStatus(OrderStatus.DELIVERED);

        when(repository.findById(orderId)).thenReturn(Optional.of(shipped));
        when(repository.save(any(Order.class))).thenReturn(delivered);

        TransitionOrderCommand cmd = new TransitionOrderCommand(
                orderId, OrderStatus.DELIVERED, repository, lockRegistry);
        Order result = cmd.execute();

        assertThat(result.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    @DisplayName("execute: appends status event with correct from/to/actor/reason")
    void execute_appendsStatusEventWithCorrectFields() {
        OrderId orderId = OrderId.generate();
        Order created = buildOrder(orderId, OrderStatus.CREATED);
        Order validated = created.withStatus(OrderStatus.VALIDATED);

        when(repository.findById(orderId)).thenReturn(Optional.of(created));
        when(repository.save(any(Order.class))).thenReturn(validated);

        TransitionOrderCommand cmd = new TransitionOrderCommand(
                orderId, OrderStatus.VALIDATED, "operator:7", "manual", repository, lockRegistry);
        cmd.execute();

        ArgumentCaptor<OrderStatusEvent> eventCaptor = ArgumentCaptor.forClass(OrderStatusEvent.class);
        verify(repository).appendStatusEvent(eventCaptor.capture());
        OrderStatusEvent event = eventCaptor.getValue();
        assertThat(event.from()).isEqualTo(OrderStatus.CREATED);
        assertThat(event.to()).isEqualTo(OrderStatus.VALIDATED);
        assertThat(event.actor()).isEqualTo("operator:7");
        assertThat(event.reason()).isEqualTo("manual");
    }

    // =========================================================================
    // execute() — invalid transitions
    // =========================================================================

    @Test
    @DisplayName("Transition from FAILED (terminal): throws IllegalStateException")
    void execute_fromFailed_throwsIllegalStateException() {
        OrderId orderId = OrderId.generate();
        Order failed = buildOrder(orderId, OrderStatus.CREATED).withFailure("some reason");

        when(repository.findById(orderId)).thenReturn(Optional.of(failed));

        TransitionOrderCommand cmd = new TransitionOrderCommand(
                orderId, OrderStatus.VALIDATED, repository, lockRegistry);

        assertThatThrownBy(cmd::execute).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Transition from DELIVERED (terminal): throws IllegalStateException")
    void execute_fromDelivered_throwsIllegalStateException() {
        OrderId orderId = OrderId.generate();
        Order delivered = buildOrder(orderId, OrderStatus.DELIVERED);

        when(repository.findById(orderId)).thenReturn(Optional.of(delivered));

        TransitionOrderCommand cmd = new TransitionOrderCommand(
                orderId, OrderStatus.SHIPPED, repository, lockRegistry);

        assertThatThrownBy(cmd::execute).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Invalid transition (CREATED → CONFIRMED): throws IllegalStateException")
    void execute_invalidTransition_throwsIllegalStateException() {
        OrderId orderId = OrderId.generate();
        Order created = buildOrder(orderId, OrderStatus.CREATED);

        when(repository.findById(orderId)).thenReturn(Optional.of(created));

        TransitionOrderCommand cmd = new TransitionOrderCommand(
                orderId, OrderStatus.CONFIRMED, repository, lockRegistry);

        assertThatThrownBy(cmd::execute).isInstanceOf(IllegalStateException.class);
    }

    // =========================================================================
    // execute() — order not found
    // =========================================================================

    @Test
    @DisplayName("execute: throws IllegalStateException when order is not found")
    void execute_orderNotFound_throwsIllegalStateException() {
        OrderId orderId = OrderId.generate();
        when(repository.findById(orderId)).thenReturn(Optional.empty());

        TransitionOrderCommand cmd = new TransitionOrderCommand(
                orderId, OrderStatus.VALIDATED, repository, lockRegistry);

        assertThatThrownBy(cmd::execute)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Order not found");
    }
}
