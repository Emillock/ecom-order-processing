package com.example.orderprocessing.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/**
 * Immutable monetary value combining an amount and a currency.
 * Enforces that the amount's scale does not exceed 4 decimal places and that
 * the currency is never {@code null}. Arithmetic operations require matching
 * currencies to prevent accidental cross-currency calculations.
 *
 * <p>Satisfies Requirements 3.4 and 3.5: grand_total arithmetic is performed
 * through this type, and non-negativity is the caller's responsibility (the
 * Pricing_Engine enforces it at the strategy level).
 */
public record Money(BigDecimal amount, Currency currency) {

    /** Maximum permitted scale for the monetary amount. */
    private static final int MAX_SCALE = 4;

    /**
     * Compact constructor that validates invariants on construction.
     *
     * <ul>
     *   <li>{@code currency} must not be {@code null}.</li>
     *   <li>{@code amount} must not be {@code null}.</li>
     *   <li>{@code amount.scale()} must be {@code <= 4}.</li>
     * </ul>
     *
     * @throws IllegalArgumentException if any invariant is violated
     */
    public Money {
        if (currency == null) {
            throw new IllegalArgumentException("Money currency must not be null");
        }
        if (amount == null) {
            throw new IllegalArgumentException("Money amount must not be null");
        }
        if (amount.scale() > MAX_SCALE) {
            throw new IllegalArgumentException(
                    "Money amount scale " + amount.scale() + " exceeds maximum of " + MAX_SCALE);
        }
    }

    /**
     * Factory method that creates a {@code Money} instance, stripping trailing
     * zeros and then validating the scale constraint.
     *
     * @param amount   the monetary amount; must have scale {@code <= 4}
     * @param currency the ISO 4217 currency; must not be {@code null}
     * @return a new {@code Money} instance
     * @throws IllegalArgumentException if any invariant is violated
     */
    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    /**
     * Convenience factory that parses a plain string amount.
     *
     * @param amount       the monetary amount as a decimal string
     * @param currencyCode the ISO 4217 currency code (e.g., {@code "USD"})
     * @return a new {@code Money} instance
     * @throws IllegalArgumentException if the amount or currency is invalid
     */
    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    /**
     * Returns a new {@code Money} whose amount is the sum of this instance and
     * {@code other}. The result is scaled to {@link #MAX_SCALE} using
     * {@link RoundingMode#HALF_UP}.
     *
     * @param other the amount to add; must share the same currency
     * @return a new {@code Money} representing the sum
     * @throws IllegalArgumentException if {@code other} is {@code null} or has a
     *                                  different currency
     */
    public Money plus(Money other) {
        requireMatchingCurrency(other);
        BigDecimal sum = this.amount.add(other.amount).setScale(MAX_SCALE, RoundingMode.HALF_UP);
        return new Money(sum, this.currency);
    }

    /**
     * Returns a new {@code Money} whose amount is the difference of this instance
     * minus {@code other}. The result is scaled to {@link #MAX_SCALE} using
     * {@link RoundingMode#HALF_UP}.
     *
     * @param other the amount to subtract; must share the same currency
     * @return a new {@code Money} representing the difference
     * @throws IllegalArgumentException if {@code other} is {@code null} or has a
     *                                  different currency
     */
    public Money minus(Money other) {
        requireMatchingCurrency(other);
        BigDecimal difference = this.amount.subtract(other.amount).setScale(MAX_SCALE, RoundingMode.HALF_UP);
        return new Money(difference, this.currency);
    }

    /**
     * Returns {@code true} if this amount is greater than or equal to zero.
     *
     * @return {@code true} when {@code amount >= 0}
     */
    public boolean isNonNegative() {
        return amount.compareTo(BigDecimal.ZERO) >= 0;
    }

    /**
     * Returns a zero-valued {@code Money} for the given currency.
     *
     * @param currency the ISO 4217 currency; must not be {@code null}
     * @return a {@code Money} with amount {@code 0.0000}
     */
    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO.setScale(MAX_SCALE, RoundingMode.UNNECESSARY), currency);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Validates that {@code other} is non-null and shares this instance's currency.
     *
     * @param other the operand to validate
     * @throws IllegalArgumentException if {@code other} is {@code null} or has a
     *                                  mismatched currency
     */
    private void requireMatchingCurrency(Money other) {
        if (other == null) {
            throw new IllegalArgumentException("Money operand must not be null");
        }
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Currency mismatch: cannot operate on " + this.currency + " and " + other.currency);
        }
    }
}
