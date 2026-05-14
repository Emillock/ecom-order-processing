package com.example.orderprocessing.domain.pricing.strategies;

import com.example.orderprocessing.domain.model.Order;
import com.example.orderprocessing.domain.pricing.PriceContribution;
import com.example.orderprocessing.domain.pricing.PricingStrategy;

import java.util.Currency;

/**
 * A no-operation pricing strategy that contributes zero discount, zero tax, and zero shipping
 * to every order.
 *
 * <p>When a chain consists entirely of {@code NoOpStrategy} instances, the grand total produced
 * by the {@link com.example.orderprocessing.domain.pricing.PricingEngine} equals the order's
 * subtotal, satisfying Requirement 20.2 (Property 5).
 *
 * <p>This strategy is idempotent: applying it any number of times yields the same zero
 * {@link PriceContribution} (Requirement 20.3).
 *
 * <p>Satisfies Requirements 3.1, 3.2, 20.2, and 20.3.
 */
public final class NoOpStrategy implements PricingStrategy {

    private final Currency currency;

    /**
     * Constructs a {@code NoOpStrategy} for the given currency.
     *
     * @param currency the ISO 4217 currency used to produce zero-valued contributions;
     *                 must not be {@code null}
     * @throws NullPointerException if {@code currency} is {@code null}
     */
    public NoOpStrategy(Currency currency) {
        if (currency == null) {
            throw new NullPointerException("currency must not be null");
        }
        this.currency = currency;
    }

    /**
     * Returns a zero-valued {@link PriceContribution} — no discount, no tax, no shipping.
     *
     * @param order the order being priced; must not be {@code null}
     * @return a {@link PriceContribution} with all amounts equal to zero
     * @throws NullPointerException if {@code order} is {@code null}
     */
    @Override
    public PriceContribution apply(Order order) {
        if (order == null) {
            throw new NullPointerException("order must not be null");
        }
        return PriceContribution.zero(currency);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isIdempotent() {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public String strategyName() {
        return "no-op";
    }
}
