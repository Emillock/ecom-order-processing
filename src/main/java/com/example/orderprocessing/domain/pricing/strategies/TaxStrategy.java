package com.example.orderprocessing.domain.pricing.strategies;

import com.example.orderprocessing.domain.model.Money;
import com.example.orderprocessing.domain.model.Order;
import com.example.orderprocessing.domain.model.OrderItem;
import com.example.orderprocessing.domain.pricing.PriceContribution;
import com.example.orderprocessing.domain.pricing.PricingStrategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/**
 * Pricing strategy that applies a configurable tax rate to the order subtotal.
 *
 * <p>The tax amount is computed as {@code subtotal * taxRatePercent / 100}, rounded to scale 4
 * using {@link RoundingMode#HALF_UP}. The subtotal is derived from the order's line items as the
 * sum of {@code quantity * unitPrice} for each item.
 *
 * <p>This strategy is idempotent: applying it twice to the same order yields the same
 * {@link PriceContribution} as applying it once (Requirement 20.3).
 *
 * <p>Satisfies Requirements 3.1, 3.2, and 20.3.
 */
public final class TaxStrategy implements PricingStrategy {

    private static final int SCALE = 4;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final BigDecimal taxRatePercent;

    /**
     * Constructs a {@code TaxStrategy} with the given tax rate percentage.
     *
     * @param taxRatePercent the tax rate as a percentage (0 to 100 inclusive); must not be
     *                       {@code null}
     * @throws NullPointerException     if {@code taxRatePercent} is {@code null}
     * @throws IllegalArgumentException if {@code taxRatePercent} is outside [0, 100]
     */
    public TaxStrategy(BigDecimal taxRatePercent) {
        if (taxRatePercent == null) {
            throw new NullPointerException("taxRatePercent must not be null");
        }
        if (taxRatePercent.compareTo(BigDecimal.ZERO) < 0
                || taxRatePercent.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException(
                    "taxRatePercent must be between 0 and 100, got: " + taxRatePercent);
        }
        this.taxRatePercent = taxRatePercent;
    }

    /**
     * Computes the tax contribution for the given order.
     *
     * <p>The tax amount equals {@code subtotal * taxRatePercent / 100}, where subtotal is the
     * sum of {@code item.quantity() * item.unitPrice().amount()} across all line items.
     *
     * @param order the order being priced; must not be {@code null}
     * @return a {@link PriceContribution} with zero discount, the computed tax, and zero shipping
     * @throws NullPointerException if {@code order} is {@code null}
     */
    @Override
    public PriceContribution apply(Order order) {
        if (order == null) {
            throw new NullPointerException("order must not be null");
        }
        Currency currency = order.getItems().get(0).unitPrice().currency();
        BigDecimal subtotal = computeSubtotal(order);
        BigDecimal taxAmount = subtotal
                .multiply(taxRatePercent)
                .divide(HUNDRED, SCALE, ROUNDING);

        Money zero = Money.zero(currency);
        Money tax = Money.of(taxAmount, currency);
        return new PriceContribution(zero, tax, zero);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isIdempotent() {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public String strategyName() {
        return "tax";
    }

    /**
     * Computes the order subtotal as the sum of {@code quantity * unitPrice} for all items.
     *
     * @param order the order whose subtotal is computed
     * @return the subtotal as a {@link BigDecimal} scaled to {@value #SCALE} decimal places
     */
    private BigDecimal computeSubtotal(Order order) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrderItem item : order.getItems()) {
            BigDecimal lineTotal = item.unitPrice().amount()
                    .multiply(BigDecimal.valueOf(item.quantity()));
            subtotal = subtotal.add(lineTotal);
        }
        return subtotal.setScale(SCALE, ROUNDING);
    }
}
