package com.example.orderprocessing.unit.domain.model;

import com.example.orderprocessing.domain.model.Money;
import com.example.orderprocessing.domain.model.Order;
import com.example.orderprocessing.domain.model.OrderBuilder;
import com.example.orderprocessing.domain.model.OrderId;
import com.example.orderprocessing.domain.model.OrderItem;
import com.example.orderprocessing.domain.model.OrderStatus;
import com.example.orderprocessing.domain.model.Sku;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pure unit tests for {@link Order} immutability and {@link OrderBuilder} invariants.
 * No Spring context is required — these tests exercise only the domain model
 * (Requirements 1.1, 3.3, 13.1).
 */
class OrderTest {

    private static final Currency USD = Currency.getInstance("USD");

    private OrderItem sampleItem;

    @BeforeEach
    void setUp() {
        sampleItem = new OrderItem(new Sku("SKU-001"), 2, Money.of(new BigDecimal("9.99"), USD));
    }

    // -------------------------------------------------------------------------
    // Helper — builds a minimal valid Order
    // -------------------------------------------------------------------------

    private Order buildMinimalOrder() {
        return new OrderBuilder()
                .id(OrderId.generate())
                .item(sampleItem)
                .build();
    }

    // -------------------------------------------------------------------------
    // Order.withStatus — immutability
    // -------------------------------------------------------------------------

    @Test
    void withStatus_returnsNewInstance() {
        Order original = buildMinimalOrder();

        Order updated = original.withStatus(OrderStatus.VALIDATED);

        assertNotSame(original, updated,
                "withStatus must return a new Order instance, not the same reference");
    }

    @Test
    void withStatus_doesNotMutateOriginalStatus() {
        Order original = buildMinimalOrder();
        OrderStatus originalStatus = original.getStatus();

        original.withStatus(OrderStatus.VALIDATED);

        assertEquals(originalStatus, original.getStatus(),
                "withStatus must leave the original Order's status unchanged");
    }

    @Test
    void withStatus_newInstanceHasTargetStatus() {
        Order original = buildMinimalOrder();

        Order updated = original.withStatus(OrderStatus.CONFIRMED);

        assertEquals(OrderStatus.CONFIRMED, updated.getStatus(),
                "withStatus must set the new status on the returned instance");
    }

    @Test
    void withStatus_preservesIdOnNewInstance() {
        Order original = buildMinimalOrder();

        Order updated = original.withStatus(OrderStatus.SHIPPED);

        assertEquals(original.getId(), updated.getId(),
                "withStatus must carry the original id over to the new instance");
    }

    @Test
    void withStatus_preservesItemsOnNewInstance() {
        Order original = buildMinimalOrder();

        Order updated = original.withStatus(OrderStatus.PRICED);

        assertEquals(original.getItems(), updated.getItems(),
                "withStatus must carry the original items over to the new instance");
    }

    @Test
    void withStatus_rejectsNullStatus() {
        Order original = buildMinimalOrder();

        assertThrows(IllegalArgumentException.class,
                () -> original.withStatus(null),
                "withStatus must throw IllegalArgumentException for a null status");
    }

    // -------------------------------------------------------------------------
    // OrderBuilder — required-field invariants
    // -------------------------------------------------------------------------

    @Test
    void builder_throwsWhenIdIsMissing() {
        assertThrows(IllegalStateException.class,
                () -> new OrderBuilder()
                        .item(sampleItem)
                        .build(),
                "build() must throw IllegalStateException when id is not set");
    }

    @Test
    void builder_throwsWhenItemsAreEmpty() {
        assertThrows(IllegalStateException.class,
                () -> new OrderBuilder()
                        .id(OrderId.generate())
                        .build(),
                "build() must throw IllegalStateException when no items have been added");
    }

    @Test
    void builder_throwsWhenBothIdAndItemsAreMissing() {
        assertThrows(IllegalStateException.class,
                () -> new OrderBuilder().build(),
                "build() must throw IllegalStateException when neither id nor items are set");
    }

    @Test
    void builder_succeedsWithMinimalRequiredFields() {
        Order order = new OrderBuilder()
                .id(OrderId.generate())
                .item(sampleItem)
                .build();

        assertEquals(OrderStatus.CREATED, order.getStatus(),
                "build() must default status to CREATED when not explicitly set");
    }

    @Test
    void builder_defaultsStatusToCreated() {
        Order order = buildMinimalOrder();

        assertEquals(OrderStatus.CREATED, order.getStatus(),
                "OrderBuilder must default status to CREATED");
    }

    @Test
    void builder_respectsExplicitStatus() {
        Order order = new OrderBuilder()
                .id(OrderId.generate())
                .item(sampleItem)
                .status(OrderStatus.VALIDATED)
                .build();

        assertEquals(OrderStatus.VALIDATED, order.getStatus(),
                "OrderBuilder must use the explicitly set status");
    }

    @Test
    void builder_rejectsNullId() {
        assertThrows(IllegalArgumentException.class,
                () -> new OrderBuilder().id(null),
                "OrderBuilder.id() must throw IllegalArgumentException for null");
    }

    @Test
    void builder_rejectsNullItem() {
        assertThrows(IllegalArgumentException.class,
                () -> new OrderBuilder().item(null),
                "OrderBuilder.item() must throw IllegalArgumentException for null");
    }
}
