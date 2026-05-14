package com.example.orderprocessing.domain.pricing;

import com.example.orderprocessing.domain.model.Money;
import com.example.orderprocessing.domain.model.Order;
import com.example.orderprocessing.domain.model.OrderItem;
import com.example.orderprocessing.domain.model.OrderStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.List;

/**
 * Applies an ordered chain of {@link PricingStrategy} instances to an {@link Order} and
 * returns a new {@code Order} with all five pricing totals populated and status transitioned
 * from {@code VALIDATED} to {@code PRICED}.
 *
 * <p>The engine is a pure domain object — it has no Spring, JPA, or Redis imports — so it can
 * be instantiated and tested without a Spring context.
 *
 * <p>Pricing algorithm (Requirement 3.4):
 * <ol>
 *   <li>Compute {@code subtotal} as the sum of {@code quantity × unitPrice} across all items.</li>
 *   <li>Apply each {@link PricingStrategy} in chain order to obtain a {@link PriceContribution}.</li>
 *   <li>Accumulate {@code discountTotal}, {@code taxTotal}, and {@code shippingTotal} from the
 *       contributions.</li>
 *   <li>Compute {@code grandTotal = subtotal − discountTotal + taxTotal + shippingTotal}.</li>
 *   <li>Return {@code order.withTotals(…).withStatus(PRICED)}.</li>
 * </ol>
 *
 * <p>If the profile name is unknown, {@link PricingStrategyChainFactory#createChain(String)}
 * throws an {@link IllegalArgumentException}; callers (e.g., {@code OrderService}) should catch
 * it and transition the order to {@code FAILED} with a pricing-error reason (Requirement 3.6).
 *
 * <p>Satisfies Requirements 3.1, 3.3, 3.4, and 13.4 (Strategy pattern — new strategies are
 * added without modifying this class).
 */
public final class PricingEngine {

    private static final int SCALE = 4;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final PricingStrategyChainFactory chainFactory;

    /**
     * Constructs a {@code PricingEngine} backed by the given {@link PricingStrategyChainFactory}.
     *
     * @param chainFactory the factory used to build the strategy chain for a given profile;
     *                     must not be {@code null}
     * @throws NullPointerException if {@code chainFactory} is {@code null}
     */
    public PricingEngine(PricingStrategyChainFactory chainFactory) {
        if (chainFactory == null) {
            throw new NullPointerException("chainFactory must not be null");
        }
        this.chainFactory = chainFactory;
    }

    /**
     * Prices the given {@link Order} using the strategy chain for the specified profile.
     *
     * <p>The method computes the subtotal from the order's line items, applies each strategy in
     * the chain to accumulate discount, tax, and shipping contributions, derives the grand total,
     * and returns a new {@code Order} with all totals set and status transitioned to
     * {@link OrderStatus#PRICED}.
     *
     * <p>The original {@code order} is not mutated; a new immutable instance is returned.
     *
     * @param order   the order to price; must not be {@code null}; expected to be in
     *                {@link OrderStatus#VALIDATED} status
     * @param profile the pricing profile name (e.g., {@code "default"}, {@code "no-op"});
     *                must not be {@code null}
     * @return a new {@code Order} with all five pricing totals populated and status set to
     *         {@link OrderStatus#PRICED}
     * @throws NullPointerException     if {@code order} or {@code profile} is {@code null}
     * @throws IllegalArgumentException if {@code profile} is unknown (propagated from
     *                                  {@link PricingStrategyChainFactory#createChain(String)});
     *                                  callers should catch this and transition the order to FAILED
     */
    public Order price(Order order, String profile) {
        if (order == null) {
            throw new NullPointerException("order must not be null");
        }
        if (profile == null) {
            throw new NullPointerException("profile must not be null");
        }

        // Throws IllegalArgumentException for unknown profiles (Requirement 3.6)
        List<PricingStrategy> chain = chainFactory.createChain(profile);

        Currency currency = resolveCurrency(order);
        Money subtotal = computeSubtotal(order, currency);

        // Accumulate contributions from each strategy in chain order
        Money discountTotal = Money.zero(currency);
        Money taxTotal = Money.zero(currency);
        Money shippingTotal = Money.zero(currency);

        for (PricingStrategy strategy : chain) {
            PriceContribution contribution = strategy.apply(order);
            discountTotal = discountTotal.plus(contribution.discountAmount());
            taxTotal = taxTotal.plus(contribution.taxAmount());
            shippingTotal = shippingTotal.plus(contribution.shippingAmount());
        }

        // grandTotal = subtotal - discountTotal + taxTotal + shippingTotal (Requirement 3.4)
        Money grandTotal = subtotal
                .minus(discountTotal)
                .plus(taxTotal)
                .plus(shippingTotal);

        return order
                .withTotals(subtotal, discountTotal, taxTotal, shippingTotal, grandTotal)
                .withStatus(OrderStatus.PRICED);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves the currency from the first item's unit price.
     *
     * <p>All items in a valid order share the same currency (enforced upstream by validation).
     *
     * @param order the order whose currency is resolved
     * @return the {@link Currency} of the order's items
     */
    private Currency resolveCurrency(Order order) {
        return order.getItems().get(0).unitPrice().currency();
    }

    /**
     * Computes the order subtotal as the sum of {@code quantity × unitPrice} for all items,
     * scaled to {@value #SCALE} decimal places using {@link RoundingMode#HALF_UP}.
     *
     * @param order    the order whose subtotal is computed
     * @param currency the currency to use for the resulting {@link Money}
     * @return the subtotal as a {@link Money} value
     */
    private Money computeSubtotal(Order order, Currency currency) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrderItem item : order.getItems()) {
            BigDecimal lineTotal = item.unitPrice().amount()
                    .multiply(BigDecimal.valueOf(item.quantity()));
            subtotal = subtotal.add(lineTotal);
        }
        return Money.of(subtotal.setScale(SCALE, ROUNDING), currency);
    }
}
