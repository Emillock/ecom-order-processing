package com.example.orderprocessing.unit.domain.lifecycle;

import com.example.orderprocessing.domain.lifecycle.OrderState;
import com.example.orderprocessing.domain.lifecycle.OrderStateRegistry;
import com.example.orderprocessing.domain.model.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link OrderStateRegistry}.
 *
 * <p>Covers: successful lookup for every status, null-argument guard, and
 * terminal-state identification via the returned state objects.
 *
 * <p>Validates: Requirements 6.1, 6.4, 13.3
 */
class OrderStateRegistryTest {

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    @DisplayName("forStatus: returns a non-null OrderState for every OrderStatus value")
    void forStatus_allStatuses_returnsNonNullState(OrderStatus status) {
        OrderState state = OrderStateRegistry.forStatus(status);
        assertThat(state).isNotNull();
    }

    @Test
    @DisplayName("forStatus: throws IllegalArgumentException when status is null")
    void forStatus_nullStatus_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> OrderStateRegistry.forStatus(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("status");
    }

    @Test
    @DisplayName("forStatus: DELIVERED state is terminal")
    void forStatus_delivered_isTerminal() {
        OrderState state = OrderStateRegistry.forStatus(OrderStatus.DELIVERED);
        assertThat(state.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("forStatus: CANCELLED state is terminal")
    void forStatus_cancelled_isTerminal() {
        OrderState state = OrderStateRegistry.forStatus(OrderStatus.CANCELLED);
        assertThat(state.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("forStatus: FAILED state is terminal")
    void forStatus_failed_isTerminal() {
        OrderState state = OrderStateRegistry.forStatus(OrderStatus.FAILED);
        assertThat(state.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("forStatus: CREATED state is not terminal")
    void forStatus_created_isNotTerminal() {
        OrderState state = OrderStateRegistry.forStatus(OrderStatus.CREATED);
        assertThat(state.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("forStatus: CONFIRMED state is not terminal")
    void forStatus_confirmed_isNotTerminal() {
        OrderState state = OrderStateRegistry.forStatus(OrderStatus.CONFIRMED);
        assertThat(state.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("forStatus: CREATED state allows transition to VALIDATED")
    void forStatus_created_allowsTransitionToValidated() {
        OrderState state = OrderStateRegistry.forStatus(OrderStatus.CREATED);
        assertThat(state.allowedTransitions()).contains(OrderStatus.VALIDATED);
    }

    @Test
    @DisplayName("forStatus: CREATED state allows transition to CANCELLED")
    void forStatus_created_allowsTransitionToCancelled() {
        OrderState state = OrderStateRegistry.forStatus(OrderStatus.CREATED);
        assertThat(state.allowedTransitions()).contains(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("forStatus: DELIVERED state has no allowed transitions (terminal)")
    void forStatus_delivered_hasNoAllowedTransitions() {
        OrderState state = OrderStateRegistry.forStatus(OrderStatus.DELIVERED);
        assertThat(state.allowedTransitions()).isEmpty();
    }

    @Test
    @DisplayName("forStatus: CONFIRMED state allows transition to SHIPPED")
    void forStatus_confirmed_allowsTransitionToShipped() {
        OrderState state = OrderStateRegistry.forStatus(OrderStatus.CONFIRMED);
        assertThat(state.allowedTransitions()).contains(OrderStatus.SHIPPED);
    }
}
