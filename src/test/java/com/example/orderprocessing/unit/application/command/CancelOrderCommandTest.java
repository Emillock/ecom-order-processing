package com.example.orderprocessing.unit.application.command;

import com.example.orderprocessing.application.command.CancelOrderCommand;
import com.example.orderprocessing.application.concurrency.OrderLockRegistry;
import com.example.orderprocessing.domain.model.Money;
import com.example.orderprocessing.domain.model.Order;
import com.example.orderprocessing.domain.model.OrderBuilder;
import com.example.orderprocessing.domain.model.OrderId;
import com.example.orderprocessing.domain.model.OrderItem;
import com.example.orderprocessing.domain.model.OrderStatus;
import com.example.orderprocessing.domain.model.OrderStatusEvent;
import com.example.orderprocessing.domain.model.Sku;
import com.example.orderprocessing.domain.port.InventoryPort;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CancelOrderCommand}.
 *
 * <p>Covers: cancel from CREATED (no inventory release), cancel from RESERVED (inventory
 * release called), cancel from CONFIRMED (inventory release called), cancel from FAILED
 * (terminal state — throws), and constructor argument validation.
 *
 * <p>Validates: Requirements 6.3, 7.1, 7.2, 7.3, 11.3, 13.3
 */
@ExtendWith(MockitoExtension.class)
class CancelOrderCommandTest {

    @Mock
    private OrderRepository repository;

    @Mock
    private InventoryPort inventoryPort;

    private OrderLockRegistry lockRegistry;

    private static final Currency USD = Currency.getInstance("USD");
    private static final List<OrderItem> ITEMS = List.of(
            new OrderItem(new Sku("SKU-001"), 2, new Money(new BigDecimal("10.00"), USD)));
    private static final String REASON = "customer request";

    @BeforeEach
    void setUp() {
        // Use a real OrderLockRegistry — it has no external dependencies
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

    private CancelOrderCommand command(OrderId orderId) {
        return new CancelOrderCommand(orderId, REASON, repository, inventoryPort, lockRegistry);
    }

    // =========================================================================
    // Constructor argument validation
    // =========================================================================

    @Test
    @DisplayName("Constructor: throws when orderId is null")
    void constructor_nullOrderId_throws() {
        assertThatThrownBy(() -> new CancelOrderCommand(null, REASON, repository, inventoryPort, lockRegistry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("orderId");
    }

    @Test
    @DisplayName("Constructor: throws when reason is null")
    void constructor_nullReason_throws() {
        assertThatThrownBy(() -> new CancelOrderCommand(OrderId.generate(), null, repository, inventoryPort, lockRegistry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason");
    }

    @Test
    @DisplayName("Constructor: throws when reason is blank")
    void constructor_blankReason_throws() {
        assertThatThrownBy(() -> new CancelOrderCommand(OrderId.generate(), "   ", repository, inventoryPort, lockRegistry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason");
    }

    @Test
    @DisplayName("Constructor: throws when repository is null")
    void constructor_nullRepository_throws() {
        assertThatThrownBy(() -> new CancelOrderCommand(OrderId.generate(), REASON, null, inventoryPort, lockRegistry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repository");
    }

    @Test
    @DisplayName("Constructor: throws when inventoryPort is null")
    void constructor_nullInventoryPort_throws() {
        assertThatThrownBy(() -> new CancelOrderCommand(OrderId.generate(), REASON, repository, null, lockRegistry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inventoryPort");
    }

    @Test
    @DisplayName("Constructor: throws when lockRegistry is null")
    void constructor_nullLockRegistry_throws() {
        assertThatThrownBy(() -> new CancelOrderCommand(OrderId.generate(), REASON, repository, inventoryPort, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lockRegistry");
    }

    @Test
    @DisplayName("getOrderId and getReason return the values supplied at construction")
    void getters_returnConstructorValues() {
        OrderId orderId = OrderId.generate();
        CancelOrderCommand cmd = new CancelOrderCommand(orderId, REASON, repository, inventoryPort, lockRegistry);

        assertThat(cmd.getOrderId()).isEqualTo(orderId);
        assertThat(cmd.getReason()).isEqualTo(REASON);
    }

    // =========================================================================
    // execute() — cancel from CREATED (no inventory release)
    // =========================================================================

    @Test
    @DisplayName("Cancel from CREATED: transitions to CANCELLED, inventory release NOT called")
    void execute_fromCreated_cancelledWithoutInventoryRelease() {
        OrderId orderId = OrderId.generate();
        Order createdOrder = buildOrder(orderId, OrderStatus.CREATED);
        Order cancelledOrder = createdOrder.withStatus(OrderStatus.CANCELLED);

        when(repository.findById(orderId)).thenReturn(Optional.of(createdOrder));
        when(repository.save(any(Order.class))).thenReturn(cancelledOrder);

        Order result = command(orderId).execute();

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventoryPort, never()).release(any(), any());
    }

    @Test
    @DisplayName("Cancel from CREATED: appends a status event to the audit log")
    void execute_fromCreated_appendsStatusEvent() {
        OrderId orderId = OrderId.generate();
        Order createdOrder = buildOrder(orderId, OrderStatus.CREATED);
        Order cancelledOrder = createdOrder.withStatus(OrderStatus.CANCELLED);

        when(repository.findById(orderId)).thenReturn(Optional.of(createdOrder));
        when(repository.save(any(Order.class))).thenReturn(cancelledOrder);

        command(orderId).execute();

        ArgumentCaptor<OrderStatusEvent> eventCaptor = ArgumentCaptor.forClass(OrderStatusEvent.class);
        verify(repository).appendStatusEvent(eventCaptor.capture());
        OrderStatusEvent event = eventCaptor.getValue();
        assertThat(event.from()).isEqualTo(OrderStatus.CREATED);
        assertThat(event.to()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(event.reason()).isEqualTo(REASON);
    }

    // =========================================================================
    // execute() — cancel from VALIDATED (no inventory release)
    // =========================================================================

    @Test
    @DisplayName("Cancel from VALIDATED: transitions to CANCELLED, inventory release NOT called")
    void execute_fromValidated_cancelledWithoutInventoryRelease() {
        OrderId orderId = OrderId.generate();
        Order validatedOrder = buildOrder(orderId, OrderStatus.VALIDATED);
        Order cancelledOrder = validatedOrder.withStatus(OrderStatus.CANCELLED);

        when(repository.findById(orderId)).thenReturn(Optional.of(validatedOrder));
        when(repository.save(any(Order.class))).thenReturn(cancelledOrder);

        Order result = command(orderId).execute();

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventoryPort, never()).release(any(), any());
    }

    // =========================================================================
    // execute() — cancel from PRICED (no inventory release)
    // =========================================================================

    @Test
    @DisplayName("Cancel from PRICED: transitions to CANCELLED, inventory release NOT called")
    void execute_fromPriced_cancelledWithoutInventoryRelease() {
        OrderId orderId = OrderId.generate();
        Order pricedOrder = buildOrder(orderId, OrderStatus.PRICED);
        Order cancelledOrder = pricedOrder.withStatus(OrderStatus.CANCELLED);

        when(repository.findById(orderId)).thenReturn(Optional.of(pricedOrder));
        when(repository.save(any(Order.class))).thenReturn(cancelledOrder);

        Order result = command(orderId).execute();

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventoryPort, never()).release(any(), any());
    }

    // =========================================================================
    // execute() — cancel from RESERVED (inventory release called)
    // =========================================================================

    @Test
    @DisplayName("Cancel from RESERVED: transitions to CANCELLED, inventory release IS called")
    void execute_fromReserved_cancelledWithInventoryRelease() {
        OrderId orderId = OrderId.generate();
        Order reservedOrder = buildOrder(orderId, OrderStatus.RESERVED);
        Order cancelledOrder = reservedOrder.withStatus(OrderStatus.CANCELLED);

        when(repository.findById(orderId)).thenReturn(Optional.of(reservedOrder));
        when(repository.save(any(Order.class))).thenReturn(cancelledOrder);

        Order result = command(orderId).execute();

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventoryPort).release(eq(orderId), eq(ITEMS));
    }

    @Test
    @DisplayName("Cancel from RESERVED: appends status event with correct from/to")
    void execute_fromReserved_appendsStatusEvent() {
        OrderId orderId = OrderId.generate();
        Order reservedOrder = buildOrder(orderId, OrderStatus.RESERVED);
        Order cancelledOrder = reservedOrder.withStatus(OrderStatus.CANCELLED);

        when(repository.findById(orderId)).thenReturn(Optional.of(reservedOrder));
        when(repository.save(any(Order.class))).thenReturn(cancelledOrder);

        command(orderId).execute();

        ArgumentCaptor<OrderStatusEvent> eventCaptor = ArgumentCaptor.forClass(OrderStatusEvent.class);
        verify(repository).appendStatusEvent(eventCaptor.capture());
        OrderStatusEvent event = eventCaptor.getValue();
        assertThat(event.from()).isEqualTo(OrderStatus.RESERVED);
        assertThat(event.to()).isEqualTo(OrderStatus.CANCELLED);
    }

    // =========================================================================
    // execute() — cancel from CONFIRMED (inventory release called)
    // =========================================================================

    @Test
    @DisplayName("Cancel from CONFIRMED: transitions to CANCELLED, inventory release IS called")
    void execute_fromConfirmed_cancelledWithInventoryRelease() {
        OrderId orderId = OrderId.generate();
        Order confirmedOrder = buildOrder(orderId, OrderStatus.CONFIRMED);
        Order cancelledOrder = confirmedOrder.withStatus(OrderStatus.CANCELLED);

        when(repository.findById(orderId)).thenReturn(Optional.of(confirmedOrder));
        when(repository.save(any(Order.class))).thenReturn(cancelledOrder);

        Order result = command(orderId).execute();

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventoryPort).release(eq(orderId), eq(ITEMS));
    }

    @Test
    @DisplayName("Cancel from CONFIRMED: appends status event with correct from/to")
    void execute_fromConfirmed_appendsStatusEvent() {
        OrderId orderId = OrderId.generate();
        Order confirmedOrder = buildOrder(orderId, OrderStatus.CONFIRMED);
        Order cancelledOrder = confirmedOrder.withStatus(OrderStatus.CANCELLED);

        when(repository.findById(orderId)).thenReturn(Optional.of(confirmedOrder));
        when(repository.save(any(Order.class))).thenReturn(cancelledOrder);

        command(orderId).execute();

        ArgumentCaptor<OrderStatusEvent> eventCaptor = ArgumentCaptor.forClass(OrderStatusEvent.class);
        verify(repository).appendStatusEvent(eventCaptor.capture());
        OrderStatusEvent event = eventCaptor.getValue();
        assertThat(event.from()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(event.to()).isEqualTo(OrderStatus.CANCELLED);
    }

    // =========================================================================
    // execute() — cancel from FAILED (terminal state — throws)
    // =========================================================================

    @Test
    @DisplayName("Cancel from FAILED: throws IllegalStateException (terminal state, invalid transition)")
    void execute_fromFailed_throwsIllegalStateException() {
        OrderId orderId = OrderId.generate();
        Order failedOrder = buildOrder(orderId, OrderStatus.CREATED).withFailure("some reason");

        when(repository.findById(orderId)).thenReturn(Optional.of(failedOrder));

        assertThatThrownBy(() -> command(orderId).execute())
                .isInstanceOf(IllegalStateException.class);

        verify(inventoryPort, never()).release(any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Cancel from CANCELLED: throws IllegalStateException (terminal state, invalid transition)")
    void execute_fromCancelled_throwsIllegalStateException() {
        OrderId orderId = OrderId.generate();
        Order cancelledOrder = buildOrder(orderId, OrderStatus.CANCELLED);

        when(repository.findById(orderId)).thenReturn(Optional.of(cancelledOrder));

        assertThatThrownBy(() -> command(orderId).execute())
                .isInstanceOf(IllegalStateException.class);

        verify(inventoryPort, never()).release(any(), any());
    }

    @Test
    @DisplayName("Cancel from DELIVERED: throws IllegalStateException (terminal state, invalid transition)")
    void execute_fromDelivered_throwsIllegalStateException() {
        OrderId orderId = OrderId.generate();
        Order deliveredOrder = buildOrder(orderId, OrderStatus.DELIVERED);

        when(repository.findById(orderId)).thenReturn(Optional.of(deliveredOrder));

        assertThatThrownBy(() -> command(orderId).execute())
                .isInstanceOf(IllegalStateException.class);

        verify(inventoryPort, never()).release(any(), any());
    }

    // =========================================================================
    // execute() — order not found
    // =========================================================================

    @Test
    @DisplayName("execute: throws IllegalStateException when order is not found in repository")
    void execute_orderNotFound_throwsIllegalStateException() {
        OrderId orderId = OrderId.generate();
        when(repository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> command(orderId).execute())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Order not found");
    }

    // =========================================================================
    // execute() — inventory release failure does not block cancellation
    // =========================================================================

    @Test
    @DisplayName("Inventory release failure: cancellation still completes (failure is swallowed)")
    void execute_inventoryReleaseThrows_cancellationStillCompletes() {
        OrderId orderId = OrderId.generate();
        Order reservedOrder = buildOrder(orderId, OrderStatus.RESERVED);
        Order cancelledOrder = reservedOrder.withStatus(OrderStatus.CANCELLED);

        when(repository.findById(orderId)).thenReturn(Optional.of(reservedOrder));
        when(repository.save(any(Order.class))).thenReturn(cancelledOrder);
        // Simulate inventory release failure
        org.mockito.Mockito.doThrow(new RuntimeException("inventory service unavailable"))
                .when(inventoryPort).release(any(), any());

        // Should not throw — inventory release failures are swallowed
        Order result = command(orderId).execute();

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(repository).save(any(Order.class));
        verify(repository).appendStatusEvent(any(OrderStatusEvent.class));
    }
}
