package com.example.orderprocessing.unit.domain.model;

import com.example.orderprocessing.domain.model.IdempotencyKey;
import com.example.orderprocessing.domain.model.Money;
import com.example.orderprocessing.domain.model.Order;
import com.example.orderprocessing.domain.model.OrderBuilder;
import com.example.orderprocessing.domain.model.OrderId;
import com.example.orderprocessing.domain.model.OrderItem;
import com.example.orderprocessing.domain.model.OrderStatus;
import com.example.orderprocessing.domain.model.OrderStatusEvent;
import com.example.orderprocessing.domain.model.Sku;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Supplementary unit tests for {@link OrderBuilder} and {@link OrderStatusEvent}
 * covering branches not exercised by existing tests.
 *
 * <p>Validates: Requirements 1.1, 6.3, 13.1
 */
class OrderBuilderSupplementTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Money UNIT_PRICE = Money.of("10.00", "USD");
    private static final OrderItem ITEM = new OrderItem(new Sku("SKU-001"), 1, UNIT_PRICE);

    // =========================================================================
    // OrderBuilder — all optional setters
    // =========================================================================

    @Test
    @DisplayName("Builder: sets all optional fields correctly")
    void builder_allOptionalFields_setCorrectly() {
        Instant created = Instant.parse("2024-01-01T00:00:00Z");
        Instant updated = Instant.parse("2024-01-02T00:00:00Z");
        IdempotencyKey key = new IdempotencyKey("test-key-123");
        Money subtotal = Money.of("10.00", "USD");
        Money discount = Money.of("1.00", "USD");
        Money tax = Money.of("0.80", "USD");
        Money shipping = Money.of("5.00", "USD");
        Money grand = Money.of("14.80", "USD");

        Order order = new OrderBuilder()
                .id(OrderId.generate())
                .item(ITEM)
                .status(OrderStatus.VALIDATED)
                .currency(USD)
                .subtotal(subtotal)
                .discountTotal(discount)
                .taxTotal(tax)
                .shippingTotal(shipping)
                .grandTotal(grand)
                .idempotencyKey(key)
                .createdAt(created)
                .updatedAt(updated)
                .failureReason("test reason")
                .build();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.VALIDATED);
        assertThat(order.getSubtotal()).isEqualTo(subtotal);
        assertThat(order.getDiscountTotal()).isEqualTo(discount);
        assertThat(order.getTaxTotal()).isEqualTo(tax);
        assertThat(order.getShippingTotal()).isEqualTo(shipping);
        assertThat(order.getGrandTotal()).isEqualTo(grand);
        assertThat(order.getIdempotencyKey()).contains(key);
        assertThat(order.getCreatedAt()).isEqualTo(created);
        assertThat(order.getUpdatedAt()).isEqualTo(updated);
        assertThat(order.getFailureReason()).contains("test reason");
    }

    @Test
    @DisplayName("Builder: items(List) replaces existing items")
    void builder_itemsList_replacesExistingItems() {
        OrderItem item2 = new OrderItem(new Sku("SKU-002"), 2, UNIT_PRICE);
        List<OrderItem> newItems = List.of(item2);

        Order order = new OrderBuilder()
                .id(OrderId.generate())
                .item(ITEM) // add first
                .items(newItems) // replace with new list
                .build();

        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getItems().get(0).sku().value()).isEqualTo("SKU-002");
    }

    @Test
    @DisplayName("Builder: throws when id is null")
    void builder_nullId_throws() {
        assertThatThrownBy(() -> new OrderBuilder().id(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Builder: throws when item is null")
    void builder_nullItem_throws() {
        assertThatThrownBy(() -> new OrderBuilder().item(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Builder: throws when items list is null")
    void builder_nullItemsList_throws() {
        assertThatThrownBy(() -> new OrderBuilder().items(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Builder: throws when status is null")
    void builder_nullStatus_throws() {
        assertThatThrownBy(() -> new OrderBuilder().status(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Builder: throws when currency is null")
    void builder_nullCurrency_throws() {
        assertThatThrownBy(() -> new OrderBuilder().currency(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Builder: throws when subtotal is null")
    void builder_nullSubtotal_throws() {
        assertThatThrownBy(() -> new OrderBuilder().subtotal(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Builder: throws when discountTotal is null")
    void builder_nullDiscountTotal_throws() {
        assertThatThrownBy(() -> new OrderBuilder().discountTotal(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Builder: throws when taxTotal is null")
    void builder_nullTaxTotal_throws() {
        assertThatThrownBy(() -> new OrderBuilder().taxTotal(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Builder: throws when shippingTotal is null")
    void builder_nullShippingTotal_throws() {
        assertThatThrownBy(() -> new OrderBuilder().shippingTotal(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Builder: throws when grandTotal is null")
    void builder_nullGrandTotal_throws() {
        assertThatThrownBy(() -> new OrderBuilder().grandTotal(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Builder: throws when idempotencyKey is null")
    void builder_nullIdempotencyKey_throws() {
        assertThatThrownBy(() -> new OrderBuilder().idempotencyKey(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Builder: throws when createdAt is null")
    void builder_nullCreatedAt_throws() {
        assertThatThrownBy(() -> new OrderBuilder().createdAt(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Builder: throws when updatedAt is null")
    void builder_nullUpdatedAt_throws() {
        assertThatThrownBy(() -> new OrderBuilder().updatedAt(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Builder: throws when failureReason is null")
    void builder_nullFailureReason_throws() {
        assertThatThrownBy(() -> new OrderBuilder().failureReason(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Builder: throws when failureReason is blank")
    void builder_blankFailureReason_throws() {
        assertThatThrownBy(() -> new OrderBuilder().failureReason("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Builder: build() throws when id is not set")
    void builder_build_noId_throws() {
        assertThatThrownBy(() -> new OrderBuilder().item(ITEM).build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("id");
    }

    @Test
    @DisplayName("Builder: build() throws when items list is empty")
    void builder_build_noItems_throws() {
        assertThatThrownBy(() -> new OrderBuilder().id(OrderId.generate()).build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("item");
    }

    // =========================================================================
    // Order.withTotals — null guards
    // =========================================================================

    @Test
    @DisplayName("Order.withTotals: throws when subtotal is null")
    void withTotals_nullSubtotal_throws() {
        Order order = new OrderBuilder().id(OrderId.generate()).item(ITEM).build();
        Money zero = Money.zero(USD);
        assertThatThrownBy(() -> order.withTotals(null, zero, zero, zero, zero))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Order.withTotals: throws when discountTotal is null")
    void withTotals_nullDiscountTotal_throws() {
        Order order = new OrderBuilder().id(OrderId.generate()).item(ITEM).build();
        Money zero = Money.zero(USD);
        assertThatThrownBy(() -> order.withTotals(zero, null, zero, zero, zero))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Order.withTotals: throws when taxTotal is null")
    void withTotals_nullTaxTotal_throws() {
        Order order = new OrderBuilder().id(OrderId.generate()).item(ITEM).build();
        Money zero = Money.zero(USD);
        assertThatThrownBy(() -> order.withTotals(zero, zero, null, zero, zero))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Order.withTotals: throws when shippingTotal is null")
    void withTotals_nullShippingTotal_throws() {
        Order order = new OrderBuilder().id(OrderId.generate()).item(ITEM).build();
        Money zero = Money.zero(USD);
        assertThatThrownBy(() -> order.withTotals(zero, zero, zero, null, zero))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Order.withTotals: throws when grandTotal is null")
    void withTotals_nullGrandTotal_throws() {
        Order order = new OrderBuilder().id(OrderId.generate()).item(ITEM).build();
        Money zero = Money.zero(USD);
        assertThatThrownBy(() -> order.withTotals(zero, zero, zero, zero, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Order.withStatus: throws when newStatus is null")
    void withStatus_nullStatus_throws() {
        Order order = new OrderBuilder().id(OrderId.generate()).item(ITEM).build();
        assertThatThrownBy(() -> order.withStatus(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Order.withFailure: throws when reason is null")
    void withFailure_nullReason_throws() {
        Order order = new OrderBuilder().id(OrderId.generate()).item(ITEM).build();
        assertThatThrownBy(() -> order.withFailure(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Order.withFailure: throws when reason is blank")
    void withFailure_blankReason_throws() {
        Order order = new OrderBuilder().id(OrderId.generate()).item(ITEM).build();
        assertThatThrownBy(() -> order.withFailure("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Order.toString: returns non-null string containing status")
    void toString_containsStatus() {
        Order order = new OrderBuilder().id(OrderId.generate()).item(ITEM).build();
        assertThat(order.toString()).contains("CREATED");
    }

    // =========================================================================
    // OrderStatusEvent — null guards
    // =========================================================================

    @Test
    @DisplayName("OrderStatusEvent: throws when orderId is null")
    void orderStatusEvent_nullOrderId_throws() {
        assertThatThrownBy(() -> new OrderStatusEvent(
                null, OrderStatus.CREATED, OrderStatus.VALIDATED, Instant.now(), "system", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("OrderStatusEvent: throws when to is null")
    void orderStatusEvent_nullTo_throws() {
        assertThatThrownBy(() -> new OrderStatusEvent(
                OrderId.generate(), OrderStatus.CREATED, null, Instant.now(), "system", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("OrderStatusEvent: throws when at is null")
    void orderStatusEvent_nullAt_throws() {
        assertThatThrownBy(() -> new OrderStatusEvent(
                OrderId.generate(), OrderStatus.CREATED, OrderStatus.VALIDATED, null, "system", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("OrderStatusEvent: throws when actor is null")
    void orderStatusEvent_nullActor_throws() {
        assertThatThrownBy(() -> new OrderStatusEvent(
                OrderId.generate(), OrderStatus.CREATED, OrderStatus.VALIDATED, Instant.now(), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("OrderStatusEvent: throws when actor is blank")
    void orderStatusEvent_blankActor_throws() {
        assertThatThrownBy(() -> new OrderStatusEvent(
                OrderId.generate(), OrderStatus.CREATED, OrderStatus.VALIDATED, Instant.now(), "  ", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("OrderStatusEvent: from is allowed to be null (initial CREATED event)")
    void orderStatusEvent_nullFrom_isAllowed() {
        OrderStatusEvent event = new OrderStatusEvent(
                OrderId.generate(), null, OrderStatus.CREATED, Instant.now(), "system", null);
        assertThat(event.from()).isNull();
        assertThat(event.to()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    @DisplayName("OrderStatusEvent: reason is allowed to be null")
    void orderStatusEvent_nullReason_isAllowed() {
        OrderStatusEvent event = new OrderStatusEvent(
                OrderId.generate(), OrderStatus.CREATED, OrderStatus.VALIDATED,
                Instant.now(), "system", null);
        assertThat(event.reason()).isNull();
    }
}
