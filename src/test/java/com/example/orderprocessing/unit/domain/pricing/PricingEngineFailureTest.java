package com.example.orderprocessing.unit.domain.pricing;

import com.example.orderprocessing.domain.model.Money;
import com.example.orderprocessing.domain.model.Order;
import com.example.orderprocessing.domain.model.OrderBuilder;
import com.example.orderprocessing.domain.model.OrderId;
import com.example.orderprocessing.domain.model.OrderItem;
import com.example.orderprocessing.domain.model.OrderStatus;
import com.example.orderprocessing.domain.model.Sku;
import com.example.orderprocessing.domain.pricing.PricingEngine;
import com.example.orderprocessing.domain.pricing.PricingStrategyChainFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the {@link PricingEngine} failure path.
 *
 * <p>Verifies that an unknown or null pricing profile causes the engine to throw
 * {@link IllegalArgumentException}, which callers (e.g., {@code OrderService}) must
 * catch and use to transition the order to {@link OrderStatus#FAILED} with a
 * {@code pricing-error} reason (Requirement 3.6).
 */
class PricingEngineFailureTest {

    private static final Currency USD = Currency.getInstance("USD");

    private PricingEngine engine;
    private Order validatedOrder;

    /**
     * Sets up a {@link PricingEngine} backed by the real {@link PricingStrategyChainFactory}
     * and a minimal validated order used across all tests.
     */
    @BeforeEach
    void setUp() {
        engine = new PricingEngine(new PricingStrategyChainFactory());
        validatedOrder = buildValidatedOrder();
    }

    /**
     * An unknown profile name must cause {@link PricingEngine#price(Order, String)} to throw
     * {@link IllegalArgumentException}.
     *
     * <p>The caller is expected to catch this exception and transition the order to
     * {@link OrderStatus#FAILED} with a {@code pricing-error} reason (Requirement 3.6).
     */
    @Test
    void unknownProfileThrowsIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> engine.price(validatedOrder, "unknown-profile"),
                "PricingEngine must throw IllegalArgumentException for an unknown profile");
    }

    /**
     * A null profile name must cause {@link PricingEngine#price(Order, String)} to throw
     * {@link NullPointerException} (the engine guards null profile directly).
     */
    @Test
    void nullProfileThrowsNullPointerException() {
        assertThrows(
                NullPointerException.class,
                () -> engine.price(validatedOrder, null),
                "PricingEngine must throw NullPointerException for a null profile");
    }

    /**
     * The exception message for an unknown profile must contain the supplied profile name
     * so that callers can include it in the {@code pricing-error} failure reason.
     */
    @Test
    void unknownProfileExceptionMessageContainsProfileName() {
        String unknownProfile = "premium-vip";
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> engine.price(validatedOrder, unknownProfile));

        assertTrue(
                ex.getMessage().contains(unknownProfile),
                () -> "Exception message should contain the unknown profile name '"
                      + unknownProfile + "', but was: " + ex.getMessage());
    }

    /**
     * Demonstrates the caller pattern: catching the exception and transitioning the order
     * to {@link OrderStatus#FAILED} with a {@code pricing-error} reason (Requirement 3.6).
     */
    @Test
    void callerCanTransitionOrderToFailedOnUnknownProfile() {
        Order failedOrder;
        try {
            engine.price(validatedOrder, "missing-profile");
            // Should not reach here
            throw new AssertionError("Expected IllegalArgumentException was not thrown");
        } catch (IllegalArgumentException e) {
            // Caller pattern: transition to FAILED with pricing-error reason
            failedOrder = validatedOrder.withFailure("pricing-error: " + e.getMessage());
        }

        assertTrue(
                failedOrder.getStatus() == OrderStatus.FAILED,
                "Order must be in FAILED status after pricing error");

        assertTrue(
                failedOrder.getFailureReason().isPresent(),
                "Order must have a failure reason after pricing error");

        String reason = failedOrder.getFailureReason().get();
        assertTrue(
                reason.startsWith("pricing-error"),
                "Failure reason must start with 'pricing-error', but was: " + reason);
    }

    /**
     * A null {@link Order} argument must cause {@link PricingEngine#price(Order, String)}
     * to throw {@link NullPointerException}.
     */
    @Test
    void nullOrderThrowsNullPointerException() {
        assertThrows(
                NullPointerException.class,
                () -> engine.price(null, "default"),
                "PricingEngine must throw NullPointerException for a null order");
    }

    /**
     * A null {@link PricingStrategyChainFactory} must cause the {@link PricingEngine}
     * constructor to throw {@link NullPointerException}.
     */
    @Test
    void nullChainFactoryThrowsNullPointerException() {
        assertThrows(
                NullPointerException.class,
                () -> new PricingEngine(null),
                "PricingEngine constructor must throw NullPointerException for a null factory");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a minimal {@link Order} in {@link OrderStatus#VALIDATED} status with one item.
     *
     * @return a valid order ready to be priced
     */
    private Order buildValidatedOrder() {
        Money price = Money.of(new BigDecimal("10.00"), USD);
        OrderItem item = new OrderItem(new Sku("SKU-001"), 2, price);
        return new OrderBuilder()
                .id(new OrderId(UUID.randomUUID()))
                .item(item)
                .status(OrderStatus.VALIDATED)
                .currency(USD)
                .build();
    }
}
