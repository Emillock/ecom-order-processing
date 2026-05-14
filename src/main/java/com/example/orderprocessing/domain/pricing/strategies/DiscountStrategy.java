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
 * Pricing strategy that applies a configurable percentage discount to the order subtotal.
 *
 * <p>The discount amount is computed as {@code subtotal * discountPercent / 100}, rounded to
 * scale 4 using {@link RoundingMode#HALF_UP}. The subtotal is derived from the order's line
 * items as the sum of {@code quantity * unitPrice} for each item.
 *
 * <p>This strategy is idempotent: applying it twice to the same order yields the same
 * {@link PriceContribution} as applying it once (Requirement 20.3).
 *
 * <p>Satisfies Requirements 3.1, 3.2, and 20.3.
 */
public final class DiscountStrategy implements PricingStrategy {

    private static final int SCALE = 4;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final BigDecimal discountPercent;

    /**
     * Constructs a {@code DiscountStrategy} with the given discount percentage.
     *
     * @param discountPercent the percentage to discount (0 to 100 inclusive); must not be
     *                        {@code null}
     * @throws NullPointerException     if {@code discountPercent} is {@code null}
     * @throws IllegalArgumentException if {@code discountPercent} is outside [0, 100]
     */
    public DiscountStrategy(BigDecimal discountPercent) {
        if (discountPercent == null) {
            throw new NullPointerException("discountPercent must not be null");
        }
        if (discountPercent.compareTo(BigDecimal.ZERO) < 0
                || discountPercent.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException(
                    "discountPercent must be between 0 and 100, got: " + discountPercent);
        }
        this.discountPercent = discountPercent;
    }

    /**
     * Computes the discount contribution for the given order.
     *
     * <p>The discount amount equals {@code subtotal * discountPercent / 100}, where subtotal is
     * the sum of {@code item.quantity() * item.unitPrice().amount()} across all line items.
     *
     * @param order the order being priced; must not be {@code null}
     * @return a {@link PriceContribution} with the computed discount and zero tax/shipping
     * @throws NullPointerException if {@code order} is {@code null}
     */
    @Override
    public PriceContribution apply(Order order) {
        if (order == null) {
            throw new NullPointerException("order must not be null");
        }
        Currency currency = order.getItems().get(0).unitPrice().currency();
        BigDecimal subtotal = computeSubtotal(order);
        BigDecimal discountAmount = subtotal
                .multiply(discountPercent)
                .divide(HUNDRED, SCALE, ROUNDING);

        Money zero = Money.zero(currency);
        Money discount = Money.of(discountAmount, currency);
        return new PriceContribution(discount, zero, zero);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isIdempotent() {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public String strategyName() {
        return "discount";
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
