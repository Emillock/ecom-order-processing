package com.example.orderprocessing.property.serialization;

import com.example.orderprocessing.domain.model.IdempotencyKey;
import com.example.orderprocessing.domain.model.Money;
import com.example.orderprocessing.domain.model.Order;
import com.example.orderprocessing.domain.model.OrderBuilder;
import com.example.orderprocessing.domain.model.OrderId;
import com.example.orderprocessing.domain.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

/**
 * Flat data-transfer record that mirrors all {@link Order} fields in a form
 * that Jackson can serialize and deserialize without requiring access to the
 * package-private {@link Order} constructor.
 *
 * <p>Used by both the serialization round-trip property test and the malformed
 * JSON rejection unit test.
 */
public record OrderDto(
        UUID id,
        List<OrderItemDto> items,
        OrderStatus status,
        BigDecimal subtotalAmount,
        String subtotalCurrency,
        BigDecimal discountTotalAmount,
        String discountTotalCurrency,
        BigDecimal taxTotalAmount,
        String taxTotalCurrency,
        BigDecimal shippingTotalAmount,
        String shippingTotalCurrency,
        BigDecimal grandTotalAmount,
        String grandTotalCurrency,
        String idempotencyKey,
        Instant createdAt,
        Instant updatedAt,
        String failureReason) {

    /**
     * Converts a domain {@link Order} to this DTO.
     *
     * @param order the domain order; must not be {@code null}
     * @return a populated {@link OrderDto}
     */
    public static OrderDto from(Order order) {
        return new OrderDto(
                order.getId().value(),
                order.getItems().stream().map(OrderItemDto::from).toList(),
                order.getStatus(),
                order.getSubtotal().amount(),
                order.getSubtotal().currency().getCurrencyCode(),
                order.getDiscountTotal().amount(),
                order.getDiscountTotal().currency().getCurrencyCode(),
                order.getTaxTotal().amount(),
                order.getTaxTotal().currency().getCurrencyCode(),
                order.getShippingTotal().amount(),
                order.getShippingTotal().currency().getCurrencyCode(),
                order.getGrandTotal().amount(),
                order.getGrandTotal().currency().getCurrencyCode(),
                order.getIdempotencyKey().map(IdempotencyKey::value).orElse(null),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getFailureReason().orElse(null));
    }

    /**
     * Reconstructs a domain {@link Order} from this DTO.
     *
     * @return a fully constructed {@link Order}
     * @throws IllegalArgumentException if any required field is null or invalid
     * @throws IllegalStateException    if the builder invariants are violated
     */
    public Order toDomain() {
        OrderBuilder builder = new OrderBuilder()
                .id(new OrderId(id))
                .items(items.stream().map(i -> i.toDomain()).toList())
                .status(status)
                .subtotal(new Money(subtotalAmount, Currency.getInstance(subtotalCurrency)))
                .discountTotal(new Money(discountTotalAmount, Currency.getInstance(discountTotalCurrency)))
                .taxTotal(new Money(taxTotalAmount, Currency.getInstance(taxTotalCurrency)))
                .shippingTotal(new Money(shippingTotalAmount, Currency.getInstance(shippingTotalCurrency)))
                .grandTotal(new Money(grandTotalAmount, Currency.getInstance(grandTotalCurrency)))
                .createdAt(createdAt)
                .updatedAt(updatedAt);
        if (idempotencyKey != null) {
            builder.idempotencyKey(new IdempotencyKey(idempotencyKey));
        }
        if (failureReason != null) {
            builder.failureReason(failureReason);
        }
        return builder.build();
    }
}
