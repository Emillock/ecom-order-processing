package com.example.orderprocessing.domain.pricing.strategies;

import com.example.orderprocessing.domain.model.Money;
import com.example.orderprocessing.domain.model.Order;
import com.example.orderprocessing.domain.pricing.PriceContribution;
import com.example.orderprocessing.domain.pricing.PricingStrategy;

import java.util.Currency;

/**
 * Pricing strategy that applies a fixed shipping fee to every order.
 *
 * <p>The shipping amount is a constant {@link Money} value supplied at construction time.
 * It is added to the order's price regardless of the order's contents or subtotal.
 *
 * <p>This strategy is idempotent: applying it twice to the same order yields the same
 * {@link PriceContribution} as applying it once (Requirement 20.3).
 *
 * <p>Satisfies Requirements 3.1, 3.2, and 20.3.
 */
public final class ShippingStrategy implements PricingStrategy {

    private final Money shippingFee;

    /**
     * Constructs a {@code ShippingStrategy} with the given fixed shipping fee.
     *
     * @param shippingFee the flat shipping fee to apply to every order; must not be {@code null}
     *                    and must be non-negative
     * @throws NullPointerException     if {@code shippingFee} is {@code null}
     * @throws IllegalArgumentException if {@code shippingFee} is negative
     */
    public ShippingStrategy(Money shippingFee) {
        if (shippingFee == null) {
            throw new NullPointerException("shippingFee must not be null");
        }
        if (!shippingFee.isNonNegative()) {
            throw new IllegalArgumentException(
                    "shippingFee must be non-negative, got: " + shippingFee.amount());
        }
        this.shippingFee = shippingFee;
    }

    /**
     * Returns a {@link PriceContribution} with zero discount, zero tax, and the configured
     * shipping fee as the shipping amount.
     *
     * @param order the order being priced; must not be {@code null}
     * @return a {@link PriceContribution} with the fixed shipping fee and zero discount/tax
     * @throws NullPointerException if {@code order} is {@code null}
     */
    @Override
    public PriceContribution apply(Order order) {
        if (order == null) {
            throw new NullPointerException("order must not be null");
        }
        Currency currency = shippingFee.currency();
        Money zero = Money.zero(currency);
        return new PriceContribution(zero, zero, shippingFee);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isIdempotent() {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public String strategyName() {
        return "shipping";
    }
}
