package com.example.orderprocessing.unit.domain.model;

import com.example.orderprocessing.domain.model.IdempotencyKey;
import com.example.orderprocessing.domain.model.Money;
import com.example.orderprocessing.domain.model.OrderId;
import com.example.orderprocessing.domain.model.OrderItem;
import com.example.orderprocessing.domain.model.OrderStatus;
import com.example.orderprocessing.domain.model.Sku;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for domain value object invariants.
 * No Spring context is required — these tests exercise only the record compact constructors
 * and enum methods defined in the domain model (Requirements 1.2, 3.5, 6.4).
 */
class ValueObjectTest {

    // -------------------------------------------------------------------------
    // Money — construction invariants
    // -------------------------------------------------------------------------

    @Test
    void money_rejectsNullCurrency() {
        assertThrows(IllegalArgumentException.class,
                () -> new Money(BigDecimal.ONE, null),
                "Money should reject a null currency");
    }

    @Test
    void money_rejectsNullAmount() {
        assertThrows(IllegalArgumentException.class,
                () -> new Money(null, Currency.getInstance("USD")),
                "Money should reject a null amount");
    }

    @Test
    void money_rejectsScaleGreaterThanFour() {
        // scale of 5 — e.g. "1.00001"
        BigDecimal tooManyDecimals = new BigDecimal("1.00001");
        assertThrows(IllegalArgumentException.class,
                () -> new Money(tooManyDecimals, Currency.getInstance("USD")),
                "Money should reject an amount with scale > 4");
    }

    @Test
    void money_acceptsScaleEqualToFour() {
        BigDecimal fourDecimals = new BigDecimal("1.0001");
        assertDoesNotThrow(() -> new Money(fourDecimals, Currency.getInstance("USD")),
                "Money should accept an amount with scale == 4");
    }

    // -------------------------------------------------------------------------
    // Money — arithmetic currency-mismatch invariants
    // -------------------------------------------------------------------------

    @Test
    void money_plus_rejectsMismatchedCurrency() {
        Money usd = Money.of("10.00", "USD");
        Money eur = Money.of("5.00", "EUR");
        assertThrows(IllegalArgumentException.class,
                () -> usd.plus(eur),
                "Money.plus should reject operands with different currencies");
    }

    @Test
    void money_minus_rejectsMismatchedCurrency() {
        Money usd = Money.of("10.00", "USD");
        Money eur = Money.of("5.00", "EUR");
        assertThrows(IllegalArgumentException.class,
                () -> usd.minus(eur),
                "Money.minus should reject operands with different currencies");
    }

    @Test
    void money_plus_rejectsNullOperand() {
        Money usd = Money.of("10.00", "USD");
        assertThrows(IllegalArgumentException.class,
                () -> usd.plus(null),
                "Money.plus should reject a null operand");
    }

    @Test
    void money_minus_rejectsNullOperand() {
        Money usd = Money.of("10.00", "USD");
        assertThrows(IllegalArgumentException.class,
                () -> usd.minus(null),
                "Money.minus should reject a null operand");
    }

    // -------------------------------------------------------------------------
    // OrderItem — quantity invariants
    // -------------------------------------------------------------------------

    @Test
    void orderItem_rejectsQuantityZero() {
        Sku sku = new Sku("SKU-001");
        Money price = Money.of("9.99", "USD");
        assertThrows(IllegalArgumentException.class,
                () -> new OrderItem(sku, 0, price),
                "OrderItem should reject quantity == 0 (boundary)");
    }

    @Test
    void orderItem_rejectsQuantityNegative() {
        Sku sku = new Sku("SKU-001");
        Money price = Money.of("9.99", "USD");
        assertThrows(IllegalArgumentException.class,
                () -> new OrderItem(sku, -1, price),
                "OrderItem should reject quantity == -1");
    }

    @Test
    void orderItem_acceptsQuantityOne() {
        Sku sku = new Sku("SKU-001");
        Money price = Money.of("9.99", "USD");
        assertDoesNotThrow(() -> new OrderItem(sku, 1, price),
                "OrderItem should accept quantity == 1 (boundary)");
    }

    // -------------------------------------------------------------------------
    // OrderStatus — isTerminal()
    // -------------------------------------------------------------------------

    @Test
    void orderStatus_isTerminal_trueForDelivered() {
        assertTrue(OrderStatus.DELIVERED.isTerminal());
    }

    @Test
    void orderStatus_isTerminal_trueForCancelled() {
        assertTrue(OrderStatus.CANCELLED.isTerminal());
    }

    @Test
    void orderStatus_isTerminal_trueForFailed() {
        assertTrue(OrderStatus.FAILED.isTerminal());
    }

    @Test
    void orderStatus_isTerminal_falseForCreated() {
        assertFalse(OrderStatus.CREATED.isTerminal());
    }

    @Test
    void orderStatus_isTerminal_falseForValidated() {
        assertFalse(OrderStatus.VALIDATED.isTerminal());
    }

    @Test
    void orderStatus_isTerminal_falseForPriced() {
        assertFalse(OrderStatus.PRICED.isTerminal());
    }

    @Test
    void orderStatus_isTerminal_falseForReserved() {
        assertFalse(OrderStatus.RESERVED.isTerminal());
    }

    @Test
    void orderStatus_isTerminal_falseForConfirmed() {
        assertFalse(OrderStatus.CONFIRMED.isTerminal());
    }

    @Test
    void orderStatus_isTerminal_falseForShipped() {
        assertFalse(OrderStatus.SHIPPED.isTerminal());
    }

    // -------------------------------------------------------------------------
    // OrderId — null UUID invariant
    // -------------------------------------------------------------------------

    @Test
    void orderId_rejectsNullUuid() {
        assertThrows(IllegalArgumentException.class,
                () -> new OrderId(null),
                "OrderId should reject a null UUID");
    }

    @Test
    void orderId_acceptsValidUuid() {
        assertDoesNotThrow(() -> new OrderId(UUID.randomUUID()),
                "OrderId should accept a valid UUID");
    }

    // -------------------------------------------------------------------------
    // Sku — null/blank invariants
    // -------------------------------------------------------------------------

    @Test
    void sku_rejectsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> new Sku(null),
                "Sku should reject a null value");
    }

    @Test
    void sku_rejectsBlank() {
        assertThrows(IllegalArgumentException.class,
                () -> new Sku("   "),
                "Sku should reject a blank value");
    }

    @Test
    void sku_rejectsEmptyString() {
        assertThrows(IllegalArgumentException.class,
                () -> new Sku(""),
                "Sku should reject an empty string");
    }

    // -------------------------------------------------------------------------
    // IdempotencyKey — null/blank invariants
    // -------------------------------------------------------------------------

    @Test
    void idempotencyKey_rejectsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> new IdempotencyKey(null),
                "IdempotencyKey should reject a null value");
    }

    @Test
    void idempotencyKey_rejectsBlank() {
        assertThrows(IllegalArgumentException.class,
                () -> new IdempotencyKey("   "),
                "IdempotencyKey should reject a blank value");
    }

    @Test
    void idempotencyKey_rejectsEmptyString() {
        assertThrows(IllegalArgumentException.class,
                () -> new IdempotencyKey(""),
                "IdempotencyKey should reject an empty string");
    }
}
