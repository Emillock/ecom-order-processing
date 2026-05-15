package com.example.orderprocessing.unit.application;

import com.example.orderprocessing.application.OrderProcessingPipeline;
import com.example.orderprocessing.domain.model.Money;
import com.example.orderprocessing.domain.model.Order;
import com.example.orderprocessing.domain.model.OrderBuilder;
import com.example.orderprocessing.domain.model.OrderId;
import com.example.orderprocessing.domain.model.OrderItem;
import com.example.orderprocessing.domain.model.OrderStatus;
import com.example.orderprocessing.domain.model.Sku;
import com.example.orderprocessing.domain.notification.NotificationDispatcher;
import com.example.orderprocessing.domain.port.AuthorizationResult;
import com.example.orderprocessing.domain.port.InventoryPort;
import com.example.orderprocessing.domain.port.PaymentPort;
import com.example.orderprocessing.domain.port.ReservationResult;
import com.example.orderprocessing.domain.pricing.PricingEngine;
import com.example.orderprocessing.domain.validation.ValidationChain;
import com.example.orderprocessing.domain.validation.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OrderProcessingPipeline} (Template Method).
 *
 * <p>Covers: full successful run → CONFIRMED, short-circuit on validation failure,
 * short-circuit on pricing failure, short-circuit on reservation failure, short-circuit
 * on payment failure, and null-argument guard on {@code run()}.
 *
 * <p>Validates: Requirements 2.1, 3.1, 4.1, 5.1, 8.1, 13.3
 */
@ExtendWith(MockitoExtension.class)
class OrderProcessingPipelineTest {

    @Mock
    private ValidationChain validationChain;

    @Mock
    private PricingEngine pricingEngine;

    @Mock
    private InventoryPort inventoryPort;

    @Mock
    private PaymentPort paymentPort;

    @Mock
    private NotificationDispatcher notificationDispatcher;

    private OrderProcessingPipeline pipeline;

    private static final Currency USD = Currency.getInstance("USD");
    private static final List<OrderItem> ITEMS = List.of(
            new OrderItem(new Sku("SKU-001"), 2, new Money(new BigDecimal("10.00"), USD)));

    // -------------------------------------------------------------------------
    // Concrete test subclass — supplies the pricing profile
    // -------------------------------------------------------------------------

    /**
     * Minimal concrete subclass of the abstract {@link OrderProcessingPipeline} for testing.
     * Uses the "default" profile and delegates all stage logic to the parent.
     */
    private class TestPipeline extends OrderProcessingPipeline {
        TestPipeline() {
            super(validationChain, pricingEngine, inventoryPort, paymentPort, notificationDispatcher);
        }

        @Override
        protected String pricingProfile() {
            return "default";
        }
    }

    @BeforeEach
    void setUp() {
        pipeline = new TestPipeline();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Order createdOrder() {
        return new OrderBuilder()
                .id(OrderId.generate())
                .items(ITEMS)
                .build();
    }

    private Order orderInStatus(OrderStatus status) {
        OrderBuilder builder = new OrderBuilder().id(OrderId.generate()).items(ITEMS);
        if (status != OrderStatus.CREATED) {
            builder.status(status);
        }
        return builder.build();
    }

    // =========================================================================
    // run() — null guard
    // =========================================================================

    @Test
    @DisplayName("run: throws IllegalArgumentException when order is null")
    void run_nullOrder_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> pipeline.run(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("order");
    }

    // =========================================================================
    // Full successful run → CONFIRMED
    // =========================================================================

    @Test
    @DisplayName("Full run: validate → price → reserve → pay → notify → CONFIRMED")
    void run_allStagesSucceed_returnsConfirmedOrder() {
        Order created = createdOrder();
        Order validated = created.withStatus(OrderStatus.VALIDATED);
        Order priced = validated.withStatus(OrderStatus.PRICED);
        Order reserved = priced.withStatus(OrderStatus.RESERVED);
        Order confirmed = reserved.withStatus(OrderStatus.CONFIRMED);

        when(validationChain.validate(any(Order.class))).thenReturn(ValidationResult.pass("ALL_PASSED"));
        when(pricingEngine.price(any(Order.class), eq("default"))).thenReturn(priced);
        when(inventoryPort.reserve(any(), any())).thenReturn(ReservationResult.success());
        when(paymentPort.authorize(any(), any())).thenReturn(AuthorizationResult.authorized());

        Order result = pipeline.run(created);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(notificationDispatcher).dispatch(any());
    }

    @Test
    @DisplayName("Full run: notification dispatcher is called exactly once on success")
    void run_allStagesSucceed_notificationDispatchedOnce() {
        Order created = createdOrder();
        Order priced = created.withStatus(OrderStatus.PRICED);

        when(validationChain.validate(any(Order.class))).thenReturn(ValidationResult.pass("ALL_PASSED"));
        when(pricingEngine.price(any(Order.class), eq("default"))).thenReturn(priced);
        when(inventoryPort.reserve(any(), any())).thenReturn(ReservationResult.success());
        when(paymentPort.authorize(any(), any())).thenReturn(AuthorizationResult.authorized());

        pipeline.run(created);

        verify(notificationDispatcher).dispatch(any());
    }

    // =========================================================================
    // Short-circuit on validation failure
    // =========================================================================

    @Test
    @DisplayName("Short-circuit: validation failure → FAILED, pricing/reservation/payment NOT called")
    void run_validationFails_shortCircuitsWithFailedOrder() {
        Order created = createdOrder();

        when(validationChain.validate(any(Order.class)))
                .thenReturn(ValidationResult.fail("NON_EMPTY_ITEMS", "Order must contain at least one item"));

        Order result = pipeline.run(created);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(result.getFailureReason()).isPresent();
        assertThat(result.getFailureReason().get()).contains("validation_failed");

        verify(pricingEngine, never()).price(any(), anyString());
        verify(inventoryPort, never()).reserve(any(), any());
        verify(paymentPort, never()).authorize(any(), any());
        verify(notificationDispatcher, never()).dispatch(any());
    }

    @Test
    @DisplayName("Short-circuit: validation failure reason includes rule ID and message")
    void run_validationFails_failureReasonContainsRuleIdAndMessage() {
        Order created = createdOrder();

        when(validationChain.validate(any(Order.class)))
                .thenReturn(ValidationResult.fail("POSITIVE_QUANTITY", "All items must have positive quantity"));

        Order result = pipeline.run(created);

        assertThat(result.getFailureReason()).hasValueSatisfying(reason -> {
            assertThat(reason).contains("POSITIVE_QUANTITY");
            assertThat(reason).contains("All items must have positive quantity");
        });
    }

    // =========================================================================
    // Short-circuit on pricing failure
    // =========================================================================

    @Test
    @DisplayName("Short-circuit: pricing failure → FAILED, reservation/payment NOT called")
    void run_pricingFails_shortCircuitsWithFailedOrder() {
        Order created = createdOrder();

        when(validationChain.validate(any(Order.class))).thenReturn(ValidationResult.pass("ALL_PASSED"));
        when(pricingEngine.price(any(Order.class), eq("default")))
                .thenThrow(new IllegalArgumentException("unknown pricing profile: default"));

        Order result = pipeline.run(created);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(result.getFailureReason()).hasValueSatisfying(r -> assertThat(r).contains("pricing_failed"));

        verify(inventoryPort, never()).reserve(any(), any());
        verify(paymentPort, never()).authorize(any(), any());
        verify(notificationDispatcher, never()).dispatch(any());
    }

    @Test
    @DisplayName("Short-circuit: pricing failure reason includes exception message")
    void run_pricingFails_failureReasonContainsExceptionMessage() {
        Order created = createdOrder();

        when(validationChain.validate(any(Order.class))).thenReturn(ValidationResult.pass("ALL_PASSED"));
        when(pricingEngine.price(any(Order.class), anyString()))
                .thenThrow(new IllegalArgumentException("no chain for profile: premium"));

        Order result = pipeline.run(created);

        assertThat(result.getFailureReason()).hasValueSatisfying(r ->
                assertThat(r).contains("no chain for profile: premium"));
    }

    // =========================================================================
    // Short-circuit on reservation failure
    // =========================================================================

    @Test
    @DisplayName("Short-circuit: reservation out-of-stock → FAILED, payment NOT called")
    void run_reservationOutOfStock_shortCircuitsWithFailedOrder() {
        Order created = createdOrder();
        Order priced = created.withStatus(OrderStatus.PRICED);

        when(validationChain.validate(any(Order.class))).thenReturn(ValidationResult.pass("ALL_PASSED"));
        when(pricingEngine.price(any(Order.class), eq("default"))).thenReturn(priced);
        when(inventoryPort.reserve(any(), any()))
                .thenReturn(ReservationResult.outOfStock(List.of(new Sku("SKU-001"))));

        Order result = pipeline.run(created);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(result.getFailureReason()).hasValueSatisfying(r ->
                assertThat(r).contains("inventory_out_of_stock"));

        verify(paymentPort, never()).authorize(any(), any());
        verify(notificationDispatcher, never()).dispatch(any());
    }

    @Test
    @DisplayName("Short-circuit: reservation technical failure → FAILED, payment NOT called")
    void run_reservationTechnicalFailure_shortCircuitsWithFailedOrder() {
        Order created = createdOrder();
        Order priced = created.withStatus(OrderStatus.PRICED);

        when(validationChain.validate(any(Order.class))).thenReturn(ValidationResult.pass("ALL_PASSED"));
        when(pricingEngine.price(any(Order.class), eq("default"))).thenReturn(priced);
        when(inventoryPort.reserve(any(), any()))
                .thenThrow(new RuntimeException("inventory service unavailable"));

        Order result = pipeline.run(created);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(result.getFailureReason()).hasValueSatisfying(r ->
                assertThat(r).contains("dependency_unavailable:inventory"));

        verify(paymentPort, never()).authorize(any(), any());
        verify(notificationDispatcher, never()).dispatch(any());
    }

    @Test
    @DisplayName("Short-circuit: reservation failed result → FAILED, payment NOT called")
    void run_reservationFailed_shortCircuitsWithFailedOrder() {
        Order created = createdOrder();
        Order priced = created.withStatus(OrderStatus.PRICED);

        when(validationChain.validate(any(Order.class))).thenReturn(ValidationResult.pass("ALL_PASSED"));
        when(pricingEngine.price(any(Order.class), eq("default"))).thenReturn(priced);
        when(inventoryPort.reserve(any(), any()))
                .thenReturn(ReservationResult.failed("provider error"));

        Order result = pipeline.run(created);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.FAILED);
        verify(paymentPort, never()).authorize(any(), any());
        verify(notificationDispatcher, never()).dispatch(any());
    }

    // =========================================================================
    // Short-circuit on payment failure
    // =========================================================================

    @Test
    @DisplayName("Short-circuit: payment declined → FAILED, notification NOT dispatched")
    void run_paymentDeclined_shortCircuitsWithFailedOrder() {
        Order created = createdOrder();
        Order priced = created.withStatus(OrderStatus.PRICED);

        when(validationChain.validate(any(Order.class))).thenReturn(ValidationResult.pass("ALL_PASSED"));
        when(pricingEngine.price(any(Order.class), eq("default"))).thenReturn(priced);
        when(inventoryPort.reserve(any(), any())).thenReturn(ReservationResult.success());
        when(paymentPort.authorize(any(), any()))
                .thenReturn(AuthorizationResult.declined("insufficient funds"));

        Order result = pipeline.run(created);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(result.getFailureReason()).hasValueSatisfying(r ->
                assertThat(r).contains("payment_declined"));

        verify(notificationDispatcher, never()).dispatch(any());
    }

    @Test
    @DisplayName("Short-circuit: payment technical failure → FAILED, notification NOT dispatched")
    void run_paymentTechnicalFailure_shortCircuitsWithFailedOrder() {
        Order created = createdOrder();
        Order priced = created.withStatus(OrderStatus.PRICED);

        when(validationChain.validate(any(Order.class))).thenReturn(ValidationResult.pass("ALL_PASSED"));
        when(pricingEngine.price(any(Order.class), eq("default"))).thenReturn(priced);
        when(inventoryPort.reserve(any(), any())).thenReturn(ReservationResult.success());
        when(paymentPort.authorize(any(), any()))
                .thenThrow(new RuntimeException("payment gateway timeout"));

        Order result = pipeline.run(created);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(result.getFailureReason()).hasValueSatisfying(r ->
                assertThat(r).contains("dependency_unavailable:payment"));

        verify(notificationDispatcher, never()).dispatch(any());
    }

    @Test
    @DisplayName("Short-circuit: payment decline reason is embedded in failure reason")
    void run_paymentDeclined_failureReasonContainsDeclineReason() {
        Order created = createdOrder();
        Order priced = created.withStatus(OrderStatus.PRICED);

        when(validationChain.validate(any(Order.class))).thenReturn(ValidationResult.pass("ALL_PASSED"));
        when(pricingEngine.price(any(Order.class), eq("default"))).thenReturn(priced);
        when(inventoryPort.reserve(any(), any())).thenReturn(ReservationResult.success());
        when(paymentPort.authorize(any(), any()))
                .thenReturn(AuthorizationResult.declined("card expired"));

        Order result = pipeline.run(created);

        assertThat(result.getFailureReason()).hasValueSatisfying(r ->
                assertThat(r).contains("card expired"));
    }

    // =========================================================================
    // Pipeline constructor guards
    // =========================================================================

    @Test
    @DisplayName("Constructor: throws when validationChain is null")
    void constructor_nullValidationChain_throws() {
        assertThatThrownBy(() -> new OrderProcessingPipeline(
                null, pricingEngine, inventoryPort, paymentPort, notificationDispatcher) {
            @Override
            protected String pricingProfile() { return "default"; }
        }).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Constructor: throws when pricingEngine is null")
    void constructor_nullPricingEngine_throws() {
        assertThatThrownBy(() -> new OrderProcessingPipeline(
                validationChain, null, inventoryPort, paymentPort, notificationDispatcher) {
            @Override
            protected String pricingProfile() { return "default"; }
        }).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Constructor: throws when inventoryPort is null")
    void constructor_nullInventoryPort_throws() {
        assertThatThrownBy(() -> new OrderProcessingPipeline(
                validationChain, pricingEngine, null, paymentPort, notificationDispatcher) {
            @Override
            protected String pricingProfile() { return "default"; }
        }).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Constructor: throws when paymentPort is null")
    void constructor_nullPaymentPort_throws() {
        assertThatThrownBy(() -> new OrderProcessingPipeline(
                validationChain, pricingEngine, inventoryPort, null, notificationDispatcher) {
            @Override
            protected String pricingProfile() { return "default"; }
        }).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Constructor: throws when notificationDispatcher is null")
    void constructor_nullNotificationDispatcher_throws() {
        assertThatThrownBy(() -> new OrderProcessingPipeline(
                validationChain, pricingEngine, inventoryPort, paymentPort, null) {
            @Override
            protected String pricingProfile() { return "default"; }
        }).isInstanceOf(IllegalArgumentException.class);
    }
}
