package com.example.orderprocessing.property.serialization;

import com.example.orderprocessing.domain.model.Money;
import com.example.orderprocessing.domain.model.OrderItem;
import com.example.orderprocessing.domain.model.Sku;

import java.math.BigDecimal;
import java.util.Currency;

/**
 * Flat data-transfer record for a single {@link OrderItem}.
 *
 * <p>Used by {@link OrderDto} to represent line items in a Jackson-serializable form.
 */
public record OrderItemDto(String sku, int quantity, BigDecimal unitPriceAmount, String unitPriceCurrency) {

    /**
     * Converts a domain {@link OrderItem} to this DTO.
     *
     * @param item the domain item; must not be {@code null}
     * @return a populated {@link OrderItemDto}
     */
    public static OrderItemDto from(OrderItem item) {
        return new OrderItemDto(
                item.sku().value(),
                item.quantity(),
                item.unitPrice().amount(),
                item.unitPrice().currency().getCurrencyCode());
    }

    /**
     * Reconstructs a domain {@link OrderItem} from this DTO.
     *
     * @return a fully constructed {@link OrderItem}
     * @throws IllegalArgumentException if any field is invalid
     */
    public OrderItem toDomain() {
        Currency currency = unitPriceCurrency != null
                ? Currency.getInstance(unitPriceCurrency)
                : Currency.getInstance("USD");
        return new OrderItem(new Sku(sku), quantity, new Money(unitPriceAmount, currency));
    }
}
