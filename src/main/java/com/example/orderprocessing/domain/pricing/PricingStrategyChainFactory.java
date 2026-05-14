package com.example.orderprocessing.domain.pricing;

import com.example.orderprocessing.domain.model.Money;
import com.example.orderprocessing.domain.pricing.strategies.DiscountStrategy;
import com.example.orderprocessing.domain.pricing.strategies.NoOpStrategy;
import com.example.orderprocessing.domain.pricing.strategies.ShippingStrategy;
import com.example.orderprocessing.domain.pricing.strategies.TaxStrategy;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;

/**
 * Factory Method implementation that builds an ordered {@link List} of {@link PricingStrategy}
 * instances from a named pricing profile.
 *
 * <p>Each profile name maps to a fixed, deterministic chain of strategies. The factory is the
 * single place where strategy construction parameters (rates, fees, currencies) are defined,
 * keeping that knowledge out of the {@link PricingEngine} and satisfying the Open/Closed
 * Principle: a new profile is added here without touching any consumer.
 *
 * <p>This class is a pure domain object — it has no Spring, JPA, or Redis imports — so it can
 * be instantiated and tested without a Spring context.
 *
 * <p>Supported profiles:
 * <ul>
 *   <li>{@code "default"} — {@code [DiscountStrategy(10%), TaxStrategy(8%), ShippingStrategy($5.00 USD)]}</li>
 *   <li>{@code "no-op"}   — {@code [NoOpStrategy(USD)]}</li>
 * </ul>
 *
 * <p>Satisfies Requirements 3.1, 3.6, and 13.1 (Factory Method creational pattern).
 */
public final class PricingStrategyChainFactory {

    private static final Currency USD = Currency.getInstance("USD");

    /**
     * Builds and returns the ordered {@link PricingStrategy} chain for the given profile name.
     *
     * <p>The returned list is a new, mutable snapshot; callers may iterate or copy it freely
     * without affecting subsequent calls to this method.
     *
     * <p>If the profile name is {@code null} or does not match any known profile, an
     * {@link IllegalArgumentException} is thrown so that the caller (e.g., {@code OrderService})
     * can catch it and transition the order to {@code FAILED} with a pricing-error reason
     * (Requirement 3.6).
     *
     * @param profileName the name of the pricing profile to build; must not be {@code null}
     * @return a non-empty, ordered list of {@link PricingStrategy} instances for the profile
     * @throws IllegalArgumentException if {@code profileName} is {@code null} or unknown
     */
    public List<PricingStrategy> createChain(String profileName) {
        if (profileName == null) {
            throw new IllegalArgumentException(
                    "Pricing profile name must not be null. "
                    + "Supported profiles: [default, no-op]");
        }

        return switch (profileName) {
            case "default" -> buildDefaultChain();
            case "no-op"   -> buildNoOpChain();
            default -> throw new IllegalArgumentException(
                    "Unknown pricing profile: \"" + profileName + "\". "
                    + "Supported profiles: [default, no-op]");
        };
    }

    // -------------------------------------------------------------------------
    // Private chain builders
    // -------------------------------------------------------------------------

    /**
     * Builds the {@code "default"} profile chain:
     * {@code [DiscountStrategy(10%), TaxStrategy(8%), ShippingStrategy($5.00 USD)]}.
     *
     * @return the default strategy chain
     */
    private List<PricingStrategy> buildDefaultChain() {
        return List.of(
                new DiscountStrategy(new BigDecimal("10")),
                new TaxStrategy(new BigDecimal("8")),
                new ShippingStrategy(Money.of("5.00", "USD"))
        );
    }

    /**
     * Builds the {@code "no-op"} profile chain: {@code [NoOpStrategy(USD)]}.
     *
     * @return the no-op strategy chain
     */
    private List<PricingStrategy> buildNoOpChain() {
        return List.of(new NoOpStrategy(USD));
    }
}
