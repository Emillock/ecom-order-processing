package com.example.orderprocessing.unit.domain.pricing;

import com.example.orderprocessing.domain.model.Money;
import com.example.orderprocessing.domain.model.Order;
import com.example.orderprocessing.domain.model.OrderBuilder;
import com.example.orderprocessing.domain.model.OrderId;
import com.example.orderprocessing.domain.model.OrderItem;
import com.example.orderprocessing.domain.model.Sku;
import com.example.orderprocessing.domain.notification.NotificationChannel;
import com.example.orderprocessing.domain.pricing.PriceContribution;
import com.example.orderprocessing.domain.pricing.strategies.DiscountStrategy;
import com.example.orderprocessing.domain.pricing.strategies.NoOpStrategy;
import com.example.orderprocessing.domain.pricing.strategies.ShippingStrategy;
import com.example.orderprocessing.domain.pricing.strategies.TaxStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for all pricing strategy implementations and {@link PriceContribution}.
 *
 * <p>Covers: DiscountStrategy, TaxStrategy, ShippingStrategy, NoOpStrategy, PriceContribution,
 * and NotificationChannel enum.
 *
 * <p>Validates: Requirements 3.1, 3.2, 3.5, 20.2, 20.3
 */
class PricingStrategiesTest {

    private static final Currency USD = Currency.getInstance("USD");

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Order orderWithItems(OrderItem... items) {
        OrderBuilder builder = new OrderBuilder().id(OrderId.generate());
        for (OrderItem item : items) {
            builder.item(item);
        }
        return builder.build();
    }

    private Order singleItemOrder(String sku, int qty, String price) {
        return orderWithItems(new OrderItem(new Sku(sku), qty, Money.of(price, "USD")));
    }

    // =========================================================================
    // DiscountStrategy
    // =========================================================================

    @Nested
    @DisplayName("DiscountStrategy")
    class DiscountStrategyTests {

        @Test
        @DisplayName("Constructor: throws when discountPercent is null")
        void constructor_nullPercent_throws() {
            assertThatThrownBy(() -> new DiscountStrategy(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Constructor: throws when discountPercent is negative")
        void constructor_negativePercent_throws() {
            assertThatThrownBy(() -> new DiscountStrategy(new BigDecimal("-1")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Constructor: throws when discountPercent exceeds 100")
        void constructor_percentOver100_throws() {
            assertThatThrownBy(() -> new DiscountStrategy(new BigDecimal("101")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("apply: 10% discount on $100 order = $10 discount")
        void apply_tenPercent_computesCorrectDiscount() {
            DiscountStrategy strategy = new DiscountStrategy(new BigDecimal("10"));
            Order order = singleItemOrder("SKU-001", 10, "10.00"); // subtotal = $100

            PriceContribution contribution = strategy.apply(order);

            assertThat(contribution.discountAmount().amount())
                    .isEqualByComparingTo(new BigDecimal("10.0000"));
            assertThat(contribution.taxAmount().amount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(contribution.shippingAmount().amount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("apply: 0% discount produces zero discount amount")
        void apply_zeroPercent_producesZeroDiscount() {
            DiscountStrategy strategy = new DiscountStrategy(BigDecimal.ZERO);
            Order order = singleItemOrder("SKU-001", 5, "20.00");

            PriceContribution contribution = strategy.apply(order);

            assertThat(contribution.discountAmount().amount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("apply: throws when order is null")
        void apply_nullOrder_throws() {
            DiscountStrategy strategy = new DiscountStrategy(new BigDecimal("10"));
            assertThatThrownBy(() -> strategy.apply(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("isIdempotent returns true")
        void isIdempotent_returnsTrue() {
            assertThat(new DiscountStrategy(new BigDecimal("5")).isIdempotent()).isTrue();
        }

        @Test
        @DisplayName("strategyName returns 'discount'")
        void strategyName_returnsDiscount() {
            assertThat(new DiscountStrategy(new BigDecimal("5")).strategyName()).isEqualTo("discount");
        }
    }

    // =========================================================================
    // TaxStrategy
    // =========================================================================

    @Nested
    @DisplayName("TaxStrategy")
    class TaxStrategyTests {

        @Test
        @DisplayName("Constructor: throws when taxRatePercent is null")
        void constructor_nullRate_throws() {
            assertThatThrownBy(() -> new TaxStrategy(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Constructor: throws when taxRatePercent is negative")
        void constructor_negativeRate_throws() {
            assertThatThrownBy(() -> new TaxStrategy(new BigDecimal("-0.01")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Constructor: throws when taxRatePercent exceeds 100")
        void constructor_rateOver100_throws() {
            assertThatThrownBy(() -> new TaxStrategy(new BigDecimal("100.01")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("apply: 20% tax on $50 order = $10 tax")
        void apply_twentyPercent_computesCorrectTax() {
            TaxStrategy strategy = new TaxStrategy(new BigDecimal("20"));
            Order order = singleItemOrder("SKU-001", 5, "10.00"); // subtotal = $50

            PriceContribution contribution = strategy.apply(order);

            assertThat(contribution.taxAmount().amount())
                    .isEqualByComparingTo(new BigDecimal("10.0000"));
            assertThat(contribution.discountAmount().amount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(contribution.shippingAmount().amount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("apply: 0% tax produces zero tax amount")
        void apply_zeroRate_producesZeroTax() {
            TaxStrategy strategy = new TaxStrategy(BigDecimal.ZERO);
            Order order = singleItemOrder("SKU-001", 2, "25.00");

            PriceContribution contribution = strategy.apply(order);

            assertThat(contribution.taxAmount().amount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("apply: throws when order is null")
        void apply_nullOrder_throws() {
            TaxStrategy strategy = new TaxStrategy(new BigDecimal("10"));
            assertThatThrownBy(() -> strategy.apply(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("isIdempotent returns true")
        void isIdempotent_returnsTrue() {
            assertThat(new TaxStrategy(new BigDecimal("8")).isIdempotent()).isTrue();
        }

        @Test
        @DisplayName("strategyName returns 'tax'")
        void strategyName_returnsTax() {
            assertThat(new TaxStrategy(new BigDecimal("8")).strategyName()).isEqualTo("tax");
        }
    }

    // =========================================================================
    // ShippingStrategy
    // =========================================================================

    @Nested
    @DisplayName("ShippingStrategy")
    class ShippingStrategyTests {

        @Test
        @DisplayName("Constructor: throws when shippingFee is null")
        void constructor_nullFee_throws() {
            assertThatThrownBy(() -> new ShippingStrategy(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Constructor: throws when shippingFee is negative")
        void constructor_negativeFee_throws() {
            assertThatThrownBy(() -> new ShippingStrategy(Money.of("-1.00", "USD")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("apply: returns fixed shipping fee regardless of order contents")
        void apply_returnsFixedShippingFee() {
            Money fee = Money.of("5.99", "USD");
            ShippingStrategy strategy = new ShippingStrategy(fee);
            Order order = singleItemOrder("SKU-001", 1, "10.00");

            PriceContribution contribution = strategy.apply(order);

            assertThat(contribution.shippingAmount().amount())
                    .isEqualByComparingTo(new BigDecimal("5.99"));
            assertThat(contribution.discountAmount().amount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(contribution.taxAmount().amount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("apply: zero shipping fee produces zero shipping amount")
        void apply_zeroFee_producesZeroShipping() {
            ShippingStrategy strategy = new ShippingStrategy(Money.zero(USD));
            Order order = singleItemOrder("SKU-001", 1, "10.00");

            PriceContribution contribution = strategy.apply(order);

            assertThat(contribution.shippingAmount().amount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("apply: throws when order is null")
        void apply_nullOrder_throws() {
            ShippingStrategy strategy = new ShippingStrategy(Money.of("5.00", "USD"));
            assertThatThrownBy(() -> strategy.apply(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("isIdempotent returns true")
        void isIdempotent_returnsTrue() {
            assertThat(new ShippingStrategy(Money.of("3.00", "USD")).isIdempotent()).isTrue();
        }

        @Test
        @DisplayName("strategyName returns 'shipping'")
        void strategyName_returnsShipping() {
            assertThat(new ShippingStrategy(Money.of("3.00", "USD")).strategyName()).isEqualTo("shipping");
        }
    }

    // =========================================================================
    // NoOpStrategy
    // =========================================================================

    @Nested
    @DisplayName("NoOpStrategy")
    class NoOpStrategyTests {

        @Test
        @DisplayName("Constructor: throws when currency is null")
        void constructor_nullCurrency_throws() {
            assertThatThrownBy(() -> new NoOpStrategy(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("apply: returns zero contribution for all components")
        void apply_returnsZeroContribution() {
            NoOpStrategy strategy = new NoOpStrategy(USD);
            Order order = singleItemOrder("SKU-001", 3, "15.00");

            PriceContribution contribution = strategy.apply(order);

            assertThat(contribution.discountAmount().amount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(contribution.taxAmount().amount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(contribution.shippingAmount().amount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("apply: throws when order is null")
        void apply_nullOrder_throws() {
            NoOpStrategy strategy = new NoOpStrategy(USD);
            assertThatThrownBy(() -> strategy.apply(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("isIdempotent returns true")
        void isIdempotent_returnsTrue() {
            assertThat(new NoOpStrategy(USD).isIdempotent()).isTrue();
        }

        @Test
        @DisplayName("strategyName returns 'no-op'")
        void strategyName_returnsNoOp() {
            assertThat(new NoOpStrategy(USD).strategyName()).isEqualTo("no-op");
        }
    }

    // =========================================================================
    // PriceContribution
    // =========================================================================

    @Nested
    @DisplayName("PriceContribution")
    class PriceContributionTests {

        @Test
        @DisplayName("Constructor: throws when discountAmount is null")
        void constructor_nullDiscount_throws() {
            Money zero = Money.zero(USD);
            assertThatThrownBy(() -> new PriceContribution(null, zero, zero))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Constructor: throws when taxAmount is null")
        void constructor_nullTax_throws() {
            Money zero = Money.zero(USD);
            assertThatThrownBy(() -> new PriceContribution(zero, null, zero))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Constructor: throws when shippingAmount is null")
        void constructor_nullShipping_throws() {
            Money zero = Money.zero(USD);
            assertThatThrownBy(() -> new PriceContribution(zero, zero, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Constructor: throws when discountAmount is negative")
        void constructor_negativeDiscount_throws() {
            Money zero = Money.zero(USD);
            Money negative = Money.of("-1.00", "USD");
            assertThatThrownBy(() -> new PriceContribution(negative, zero, zero))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Constructor: throws when taxAmount is negative")
        void constructor_negativeTax_throws() {
            Money zero = Money.zero(USD);
            Money negative = Money.of("-0.01", "USD");
            assertThatThrownBy(() -> new PriceContribution(zero, negative, zero))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Constructor: throws when shippingAmount is negative")
        void constructor_negativeShipping_throws() {
            Money zero = Money.zero(USD);
            Money negative = Money.of("-5.00", "USD");
            assertThatThrownBy(() -> new PriceContribution(zero, zero, negative))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("zero: returns contribution with all amounts equal to zero")
        void zero_returnsAllZeroContribution() {
            PriceContribution zero = PriceContribution.zero(USD);

            assertThat(zero.discountAmount().amount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(zero.taxAmount().amount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(zero.shippingAmount().amount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("zero: throws when currency is null")
        void zero_nullCurrency_throws() {
            assertThatThrownBy(() -> PriceContribution.zero(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // =========================================================================
    // NotificationChannel enum
    // =========================================================================

    @Nested
    @DisplayName("NotificationChannel")
    class NotificationChannelTests {

        @Test
        @DisplayName("EMAIL channelName returns 'email'")
        void email_channelName_returnsEmail() {
            assertThat(NotificationChannel.EMAIL.channelName()).isEqualTo("email");
        }

        @Test
        @DisplayName("SMS channelName returns 'sms'")
        void sms_channelName_returnsSms() {
            assertThat(NotificationChannel.SMS.channelName()).isEqualTo("sms");
        }

        @Test
        @DisplayName("WEBHOOK channelName returns 'webhook'")
        void webhook_channelName_returnsWebhook() {
            assertThat(NotificationChannel.WEBHOOK.channelName()).isEqualTo("webhook");
        }

        @Test
        @DisplayName("All three channels have distinct names")
        void allChannels_haveDistinctNames() {
            assertThat(NotificationChannel.EMAIL.channelName())
                    .isNotEqualTo(NotificationChannel.SMS.channelName());
            assertThat(NotificationChannel.EMAIL.channelName())
                    .isNotEqualTo(NotificationChannel.WEBHOOK.channelName());
            assertThat(NotificationChannel.SMS.channelName())
                    .isNotEqualTo(NotificationChannel.WEBHOOK.channelName());
        }
    }
}
