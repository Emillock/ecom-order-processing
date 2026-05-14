package com.example.orderprocessing.property.serialization;

import com.example.orderprocessing.domain.model.IdempotencyKey;
import com.example.orderprocessing.domain.model.Money;
import com.example.orderprocessing.domain.model.Order;
import com.example.orderprocessing.domain.model.OrderBuilder;
import com.example.orderprocessing.domain.model.OrderId;
import com.example.orderprocessing.domain.model.OrderItem;
import com.example.orderprocessing.domain.model.OrderStatus;
import com.example.orderprocessing.domain.model.Sku;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for JSON serialization round-trip correctness of {@link Order}.
 *
 * <p>Uses an {@link ObjectMapper} configured identically to {@code JacksonConfig}
 * (ISO-8601 timestamps, JavaTimeModule, fail-on-unknown-properties) together with
 * the shared {@link OrderDto} / {@link OrderItemDto} records that provide a
 * Jackson-serializable mirror of the domain model.
 *
 * <p><b>Property 8: For all valid Orders, {@code deserialize(serialize(o))} is
 * semantically equivalent to {@code o}.</b>
 *
 * <p><b>Validates: Requirements 18.3</b>
 */
class OrderSerializationRoundTripProperties {

    // -------------------------------------------------------------------------
    // ObjectMapper — configured identically to JacksonConfig
    // -------------------------------------------------------------------------

    private static final ObjectMapper MAPPER = buildMapper();

    private static ObjectMapper buildMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.registerModule(new JavaTimeModule());
        // Records expose their components via accessor methods; also enable field visibility
        // so that Jackson can handle both records and plain classes consistently.
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        return mapper;
    }

    // -------------------------------------------------------------------------
    // Generators
    // -------------------------------------------------------------------------

    /**
     * Provides a valid {@link OrderItem} generator using USD as the fixed currency
     * so that all items in a generated order share the same currency as the totals.
     *
     * @return arbitrary over valid {@link OrderItem} instances
     */
    @Provide
    Arbitrary<OrderItem> anyOrderItem() {
        Arbitrary<String> skuArb = Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(20);
        Arbitrary<Integer> quantityArb = Arbitraries.integers().between(1, 100);
        Arbitrary<BigDecimal> priceArb = Arbitraries.bigDecimals()
                .between(new BigDecimal("0.0001"), new BigDecimal("9999.9999"))
                .ofScale(4);

        return Combinators.combine(skuArb, quantityArb, priceArb)
                .as((sku, qty, price) ->
                        new OrderItem(new Sku(sku), qty, new Money(price, Currency.getInstance("USD"))));
    }

    /**
     * Provides a valid {@link Order} generator covering all statuses and optional fields.
     *
     * @return arbitrary over valid {@link Order} instances
     */
    @Provide
    Arbitrary<Order> anyOrder() {
        Arbitrary<OrderStatus> statusArb = Arbitraries.of(OrderStatus.values());
        Arbitrary<java.util.List<OrderItem>> itemsArb =
                anyOrderItem().list().ofMinSize(1).ofMaxSize(5);
        Arbitrary<BigDecimal> amountArb = Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, new BigDecimal("99999.9999"))
                .ofScale(4);
        Arbitrary<Boolean> hasIdempotencyKey = Arbitraries.of(true, false);
        Arbitrary<String> idempotencyKeyArb =
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(36);
        Arbitrary<String> failureReasonArb =
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50);

        // jqwik Combinators.combine supports at most 8 arbitraries; split into two steps.
        // First combine: status, items, and the four monetary amounts.
        Arbitrary<Object[]> baseArb = Combinators.combine(
                        statusArb, itemsArb,
                        amountArb, amountArb, amountArb, amountArb)
                .as((status, items, sub, disc, tax, ship) ->
                        new Object[]{status, items, sub, disc, tax, ship});

        return Combinators.combine(baseArb, hasIdempotencyKey, idempotencyKeyArb, failureReasonArb)
                .as((base, withKey, keyVal, failReason) -> {
                    OrderStatus status = (OrderStatus) base[0];
                    @SuppressWarnings("unchecked")
                    java.util.List<OrderItem> items = (java.util.List<OrderItem>) base[1];
                    BigDecimal sub  = (BigDecimal) base[2];
                    BigDecimal disc = (BigDecimal) base[3];
                    BigDecimal tax  = (BigDecimal) base[4];
                    BigDecimal ship = (BigDecimal) base[5];

                    Currency usd = Currency.getInstance("USD");
                    Money subtotal = new Money(sub, usd);
                    Money discount = new Money(disc, usd);
                    Money taxTotal = new Money(tax, usd);
                    Money shipping = new Money(ship, usd);

                    // grand = subtotal - discount + tax + shipping, clamped to 0 if negative
                    BigDecimal grandAmt = sub.subtract(disc).add(tax).add(ship);
                    if (grandAmt.compareTo(BigDecimal.ZERO) < 0) {
                        grandAmt = BigDecimal.ZERO.setScale(4);
                    }
                    Money grand = new Money(grandAmt, usd);

                    OrderBuilder builder = new OrderBuilder()
                            .id(OrderId.generate())
                            .items(items)
                            .status(status)
                            .subtotal(subtotal)
                            .discountTotal(discount)
                            .taxTotal(taxTotal)
                            .shippingTotal(shipping)
                            .grandTotal(grand)
                            .createdAt(Instant.now())
                            .updatedAt(Instant.now());

                    if (withKey) {
                        builder.idempotencyKey(new IdempotencyKey(keyVal));
                    }
                    // Only attach failureReason when status is FAILED
                    if (status == OrderStatus.FAILED) {
                        builder.failureReason(failReason);
                    }

                    return builder.build();
                });
    }

    // -------------------------------------------------------------------------
    // Property 8
    // -------------------------------------------------------------------------

    /**
     * Property 8: For all valid Orders, {@code deserialize(serialize(o))} is
     * semantically equivalent to {@code o}.
     *
     * <p>Serializes an {@link Order} to JSON via the flat {@link OrderDto} and
     * reconstructs it back to a domain {@link Order}. The reconstructed order
     * must be field-for-field equivalent to the original.
     *
     * <p><b>Validates: Requirements 18.3</b>
     *
     * @param order a valid {@link Order} generated by jqwik
     */
    @Property
    void serializationRoundTripPreservesSemanticEquivalence(
            @ForAll("anyOrder") Order order) throws Exception {

        // Serialize: Order → DTO → JSON
        OrderDto originalDto = OrderDto.from(order);
        String json = MAPPER.writeValueAsString(originalDto);

        // Deserialize: JSON → DTO → Order
        OrderDto restoredDto = MAPPER.readValue(json, OrderDto.class);
        Order restored = restoredDto.toDomain();

        // Assert semantic equivalence on all observable fields
        assertThat(restored.getId()).isEqualTo(order.getId());
        assertThat(restored.getStatus()).isEqualTo(order.getStatus());
        assertThat(restored.getItems()).hasSize(order.getItems().size());

        for (int i = 0; i < order.getItems().size(); i++) {
            OrderItem orig = order.getItems().get(i);
            OrderItem rest = restored.getItems().get(i);
            assertThat(rest.sku()).isEqualTo(orig.sku());
            assertThat(rest.quantity()).isEqualTo(orig.quantity());
            assertThat(rest.unitPrice().amount().compareTo(orig.unitPrice().amount())).isZero();
            assertThat(rest.unitPrice().currency()).isEqualTo(orig.unitPrice().currency());
        }

        assertThat(restored.getSubtotal().amount().compareTo(order.getSubtotal().amount())).isZero();
        assertThat(restored.getDiscountTotal().amount().compareTo(order.getDiscountTotal().amount())).isZero();
        assertThat(restored.getTaxTotal().amount().compareTo(order.getTaxTotal().amount())).isZero();
        assertThat(restored.getShippingTotal().amount().compareTo(order.getShippingTotal().amount())).isZero();
        assertThat(restored.getGrandTotal().amount().compareTo(order.getGrandTotal().amount())).isZero();

        assertThat(restored.getIdempotencyKey()).isEqualTo(order.getIdempotencyKey());
        assertThat(restored.getFailureReason()).isEqualTo(order.getFailureReason());

        // Timestamps: compare to millisecond precision (ISO-8601 round-trip is exact to nanos,
        // but Instant.now() may have sub-millisecond precision that survives the round-trip)
        assertThat(restored.getCreatedAt().toEpochMilli()).isEqualTo(order.getCreatedAt().toEpochMilli());
        assertThat(restored.getUpdatedAt().toEpochMilli()).isEqualTo(order.getUpdatedAt().toEpochMilli());
    }
}
