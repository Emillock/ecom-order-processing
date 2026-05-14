package com.example.orderprocessing.infrastructure.cache;

import com.example.orderprocessing.domain.model.Money;
import com.example.orderprocessing.domain.model.OrderItem;
import com.example.orderprocessing.domain.model.Sku;

import java.math.BigDecimal;
import java.util.Currency;

/**
 * Flat data-transfer record for a single {@link OrderItem} used by the Redis cache adapter.
 *
 * <p>Provides a Jackson-serializable mirror of the domain {@link OrderItem} so that
 * the cache payload can be serialized and deserialized without depending on the
 * package-private {@link OrderItem} constructor.
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
