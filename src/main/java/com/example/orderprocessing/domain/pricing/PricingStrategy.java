package com.example.orderprocessing.domain.pricing;

import com.example.orderprocessing.domain.model.Order;

/**
 * Strategy port for a single pricing algorithm applied to an {@link Order}.
 *
 * <p>Implementations compute one component of the Order's final price (e.g., a discount, a tax
 * rate, or a shipping fee) and return the result as a {@link PriceContribution}. The
 * {@link com.example.orderprocessing.domain.pricing.PricingEngine} applies an ordered chain of
 * strategies and accumulates their contributions.
 *
 * <p><strong>LSP postcondition:</strong> every implementation MUST return a
 * {@link PriceContribution} whose {@code discountAmount}, {@code taxAmount}, and
 * {@code shippingAmount} are non-negative. This postcondition is enforced structurally by
 * {@link PriceContribution}'s compact constructor, satisfying Requirement 3.5 and Property 4.
 *
 * <p>Adding a new pricing algorithm requires only adding a new implementation of this interface;
 * the {@code PricingEngine} and the strategy chain factory are not modified (Open/Closed
 * Principle, Requirement 14.2).
 *
 * <p>Satisfies Requirements 3.2, 3.5, 13.4, and 14.2.
 */
public interface PricingStrategy {

    /**
     * Computes this strategy's contribution to the final price of the given {@link Order}.
     *
     * <p><strong>Postcondition (LSP):</strong> the returned {@link PriceContribution} must have
     * non-negative {@code discountAmount}, {@code taxAmount}, and {@code shippingAmount}. This is
     * enforced by {@link PriceContribution}'s compact constructor.
     *
     * @param order the Order being priced; must not be {@code null}
     * @return a non-null {@link PriceContribution} representing this strategy's effect on the
     *         Order's price components
     */
    PriceContribution apply(Order order);

    /**
     * Returns {@code true} if applying this strategy twice to the same Order yields the same
     * {@link PriceContribution} as applying it once.
     *
     * <p>This flag is used by Property 6 to verify that idempotent strategy chains produce a
     * stable grand total when applied more than once (Requirement 20.3).
     *
     * @return {@code true} when this strategy is idempotent; {@code false} otherwise
     */
    boolean isIdempotent();

    /**
     * Returns a stable, human-readable identifier for this pricing strategy.
     *
     * <p>The name must be consistent across JVM restarts and must uniquely identify the strategy
     * type within a chain. It is used for logging, auditing, and chain-factory configuration.
     *
     * @return a non-null, non-blank strategy name (e.g., {@code "discount"}, {@code "tax"},
     *         {@code "shipping"}, {@code "no-op"})
     */
    String strategyName();
}
