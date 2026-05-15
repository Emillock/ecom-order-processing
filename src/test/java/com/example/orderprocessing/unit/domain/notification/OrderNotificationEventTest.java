package com.example.orderprocessing.unit.domain.notification;

import com.example.orderprocessing.domain.model.OrderId;
import com.example.orderprocessing.domain.model.OrderStatus;
import com.example.orderprocessing.domain.notification.OrderNotificationEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link OrderNotificationEvent}.
 *
 * <p>Covers: successful construction, null-argument guards, and field accessors.
 *
 * <p>Validates: Requirements 8.1, 8.4
 */
class OrderNotificationEventTest {

    @Test
    @DisplayName("Construction: creates event with all required fields")
    void construction_validArgs_createsEvent() {
        OrderId orderId = OrderId.generate();
        Instant now = Instant.now();

        OrderNotificationEvent event = new OrderNotificationEvent(
                orderId, OrderStatus.CONFIRMED, now);

        assertThat(event.orderId()).isEqualTo(orderId);
        assertThat(event.newStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(event.transitionTimestamp()).isEqualTo(now);
    }

    @Test
    @DisplayName("Construction: throws when orderId is null")
    void construction_nullOrderId_throws() {
        assertThatThrownBy(() -> new OrderNotificationEvent(
                null, OrderStatus.CONFIRMED, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("orderId");
    }

    @Test
    @DisplayName("Construction: throws when newStatus is null")
    void construction_nullNewStatus_throws() {
        assertThatThrownBy(() -> new OrderNotificationEvent(
                OrderId.generate(), null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("newStatus");
    }

    @Test
    @DisplayName("Construction: throws when transitionTimestamp is null")
    void construction_nullTransitionTimestamp_throws() {
        assertThatThrownBy(() -> new OrderNotificationEvent(
                OrderId.generate(), OrderStatus.CONFIRMED, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transitionTimestamp");
    }

    @Test
    @DisplayName("Construction: works for all non-null OrderStatus values")
    void construction_allOrderStatuses_succeed() {
        for (OrderStatus status : OrderStatus.values()) {
            OrderNotificationEvent event = new OrderNotificationEvent(
                    OrderId.generate(), status, Instant.now());
            assertThat(event.newStatus()).isEqualTo(status);
        }
    }

    @Test
    @DisplayName("Record equality: two events with same fields are equal")
    void equality_sameFields_areEqual() {
        OrderId orderId = OrderId.generate();
        Instant now = Instant.now();

        OrderNotificationEvent e1 = new OrderNotificationEvent(orderId, OrderStatus.SHIPPED, now);
        OrderNotificationEvent e2 = new OrderNotificationEvent(orderId, OrderStatus.SHIPPED, now);

        assertThat(e1).isEqualTo(e2);
        assertThat(e1.hashCode()).isEqualTo(e2.hashCode());
    }

    @Test
    @DisplayName("Record toString: contains orderId and status")
    void toString_containsKeyFields() {
        OrderId orderId = OrderId.generate();
        OrderNotificationEvent event = new OrderNotificationEvent(
                orderId, OrderStatus.CANCELLED, Instant.now());

        String str = event.toString();
        assertThat(str).contains("CANCELLED");
    }
}
