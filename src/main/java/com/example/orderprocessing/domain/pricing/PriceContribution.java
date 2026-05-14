package com.example.orderprocessing.domain.pricing;

import com.example.orderprocessing.domain.model.Money;

import java.util.Currency;

/**
 * Immutable record representing the monetary contribution that a single {@link PricingStrategy}
 * makes to an Order's final price.
 *
 * <p>Each field captures one component of the pricing breakdown:
 * <ul>
 *   <li>{@code discountAmount} — the reduction applied to the base price (e.g., a coupon or
 *       promotional discount).</li>
 *   <li>{@code taxAmount} — the tax charge added to the base price.</li>
 *   <li>{@code shippingAmount} — the shipping fee added to the base price.</li>
 * </ul>
 *
 * <p>All amounts must be non-negative. The compact constructor enforces this invariant so that
 * no {@code PriceContribution} can be constructed with a negative component, satisfying the
 * LSP postcondition documented on {@link PricingStrategy} (Requirements 3.5, 14.2).
 *
 * <p>Use {@link #zero(Currency)} to obtain a neutral contribution that has no effect on any
 * pricing total when accumulated by the
 * {@link com.example.orderprocessing.domain.pricing.PricingEngine}.
 *
 * <p>Satisfies Requirements 3.2, 3.5, 13.4, and 14.2.
 */
public record PriceContribution(
        Money discountAmount,
        Money taxAmount,
        Money shippingAmount) {

    /**
     * Compact constructor that validates all fields are non-null and non-negative.
     *
     * @throws NullPointerException     if any field is {@code null}
     * @throws IllegalArgumentException if any monetary amount is negative
     */
    public PriceContribution {
        if (discountAmount == null) {
            throw new NullPointerException("discountAmount must not be null");
        }
        if (taxAmount == null) {
            throw new NullPointerException("taxAmount must not be null");
        }
        if (shippingAmount == null) {
            throw new NullPointerException("shippingAmount must not be null");
        }
        if (!discountAmount.isNonNegative()) {
            throw new IllegalArgumentException("discountAmount must be non-negative, got: " + discountAmount.amount());
        }
        if (!taxAmount.isNonNegative()) {
            throw new IllegalArgumentException("taxAmount must be non-negative, got: " + taxAmount.amount());
        }
        if (!shippingAmount.isNonNegative()) {
            throw new IllegalArgumentException("shippingAmount must be non-negative, got: " + shippingAmount.amount());
        }
    }

    /**
     * Returns a zero-valued {@code PriceContribution} in the given currency. All three monetary
     * components are set to zero, making this a neutral element when accumulating contributions
     * across a strategy chain.
     *
     * @param currency the ISO 4217 currency for all zero amounts; must not be {@code null}
     * @return a new {@code PriceContribution} with all amounts equal to zero
     * @throws NullPointerException if {@code currency} is {@code null}
     */
    public static PriceContribution zero(Currency currency) {
        if (currency == null) {
            throw new NullPointerException("currency must not be null");
        }
        Money zero = Money.zero(currency);
        return new PriceContribution(zero, zero, zero);
    }
}
