package com.example.orderprocessing.unit.infrastructure.cache;

import com.example.orderprocessing.domain.model.Money;
import com.example.orderprocessing.domain.model.Order;
import com.example.orderprocessing.domain.model.OrderBuilder;
import com.example.orderprocessing.domain.model.OrderId;
import com.example.orderprocessing.domain.model.OrderItem;
import com.example.orderprocessing.domain.model.OrderStatus;
import com.example.orderprocessing.domain.model.OrderStatusEvent;
import com.example.orderprocessing.domain.model.Sku;
import com.example.orderprocessing.domain.port.OrderCachePort;
import com.example.orderprocessing.domain.port.OrderQuery;
import com.example.orderprocessing.domain.port.OrderRepository;
import com.example.orderprocessing.infrastructure.cache.CachingOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CachingOrderRepository}.
 *
 * <p>Covers: cache hit (no delegate call), cache miss (delegate called + cache populated),
 * cache unavailable (bypass + degraded log), save evicts cache, save with unavailable cache
 * (no eviction), and delegation of appendStatusEvent/search/findEventsByOrderId.
 *
 * <p>Validates: Requirements 9.3, 9.4, 9.5, 10.3, 13.2
 */
@ExtendWith(MockitoExtension.class)
class CachingOrderRepositoryTest {

    @Mock
    private OrderRepository delegate;

    @Mock
    private OrderCachePort cache;

    private CachingOrderRepository cachingRepo;

    private static final Currency USD = Currency.getInstance("USD");
    private static final List<OrderItem> ITEMS = List.of(
            new OrderItem(new Sku("SKU-001"), 1, new Money(new BigDecimal("10.00"), USD)));

    @BeforeEach
    void setUp() {
        cachingRepo = new CachingOrderRepository(delegate, cache);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Order buildOrder(OrderId id) {
        return new OrderBuilder().id(id).items(ITEMS).build();
    }

    // =========================================================================
    // findById — cache available
    // =========================================================================

    @Test
    @DisplayName("findById: cache hit — returns cached order without calling delegate")
    void findById_cacheHit_returnsCachedOrderWithoutDelegate() {
        OrderId orderId = OrderId.generate();
        Order cachedOrder = buildOrder(orderId);

        when(cache.isAvailable()).thenReturn(true);
        when(cache.get(orderId)).thenReturn(Optional.of(cachedOrder));

        Optional<Order> result = cachingRepo.findById(orderId);

        assertThat(result).contains(cachedOrder);
        verify(delegate, never()).findById(any());
    }

    @Test
    @DisplayName("findById: cache miss — calls delegate and populates cache")
    void findById_cacheMiss_callsDelegateAndPopulatesCache() {
        OrderId orderId = OrderId.generate();
        Order order = buildOrder(orderId);

        when(cache.isAvailable()).thenReturn(true);
        when(cache.get(orderId)).thenReturn(Optional.empty());
        when(delegate.findById(orderId)).thenReturn(Optional.of(order));

        Optional<Order> result = cachingRepo.findById(orderId);

        assertThat(result).contains(order);
        verify(delegate).findById(orderId);
        verify(cache).put(orderId, order);
    }

    @Test
    @DisplayName("findById: cache miss, delegate returns empty — cache not populated")
    void findById_cacheMiss_delegateReturnsEmpty_cacheNotPopulated() {
        OrderId orderId = OrderId.generate();

        when(cache.isAvailable()).thenReturn(true);
        when(cache.get(orderId)).thenReturn(Optional.empty());
        when(delegate.findById(orderId)).thenReturn(Optional.empty());

        Optional<Order> result = cachingRepo.findById(orderId);

        assertThat(result).isEmpty();
        verify(cache, never()).put(any(), any());
    }

    // =========================================================================
    // findById — cache unavailable (degraded path)
    // =========================================================================

    @Test
    @DisplayName("findById: cache unavailable — bypasses cache and calls delegate directly")
    void findById_cacheUnavailable_bypassesCacheAndCallsDelegate() {
        OrderId orderId = OrderId.generate();
        Order order = buildOrder(orderId);

        when(cache.isAvailable()).thenReturn(false);
        when(delegate.findById(orderId)).thenReturn(Optional.of(order));

        Optional<Order> result = cachingRepo.findById(orderId);

        assertThat(result).contains(order);
        verify(cache, never()).get(any());
        verify(cache, never()).put(any(), any());
    }

    // =========================================================================
    // save — cache available
    // =========================================================================

    @Test
    @DisplayName("save: delegates to underlying repository and evicts cache entry")
    void save_delegatesAndEvictsCache() {
        OrderId orderId = OrderId.generate();
        Order order = buildOrder(orderId);

        when(cache.isAvailable()).thenReturn(true);
        when(delegate.save(order)).thenReturn(order);

        Order result = cachingRepo.save(order);

        assertThat(result).isSameAs(order);
        verify(delegate).save(order);
        verify(cache).evict(orderId);
    }

    // =========================================================================
    // save — cache unavailable
    // =========================================================================

    @Test
    @DisplayName("save: cache unavailable — delegates to repository, skips eviction")
    void save_cacheUnavailable_delegatesWithoutEviction() {
        OrderId orderId = OrderId.generate();
        Order order = buildOrder(orderId);

        when(cache.isAvailable()).thenReturn(false);
        when(delegate.save(order)).thenReturn(order);

        Order result = cachingRepo.save(order);

        assertThat(result).isSameAs(order);
        verify(delegate).save(order);
        verify(cache, never()).evict(any());
    }

    // =========================================================================
    // appendStatusEvent — delegates directly
    // =========================================================================

    @Test
    @DisplayName("appendStatusEvent: delegates directly to underlying repository")
    void appendStatusEvent_delegatesDirectly() {
        OrderStatusEvent event = new OrderStatusEvent(
                OrderId.generate(), null, OrderStatus.CREATED,
                java.time.Instant.now(), "system", null);

        cachingRepo.appendStatusEvent(event);

        verify(delegate).appendStatusEvent(event);
    }

    // =========================================================================
    // search — delegates directly
    // =========================================================================

    @Test
    @DisplayName("search: delegates directly to underlying repository")
    void search_delegatesDirectly() {
        OrderQuery query = new OrderQuery(null, null, null, null);
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Order> page = new PageImpl<>(List.of());

        when(delegate.search(query, pageable)).thenReturn(page);

        Page<Order> result = cachingRepo.search(query, pageable);

        assertThat(result).isSameAs(page);
        verify(delegate).search(query, pageable);
    }

    // =========================================================================
    // findEventsByOrderId — delegates directly
    // =========================================================================

    @Test
    @DisplayName("findEventsByOrderId: delegates directly to underlying repository")
    void findEventsByOrderId_delegatesDirectly() {
        OrderId orderId = OrderId.generate();
        List<OrderStatusEvent> events = List.of();

        when(delegate.findEventsByOrderId(orderId)).thenReturn(events);

        List<OrderStatusEvent> result = cachingRepo.findEventsByOrderId(orderId);

        assertThat(result).isSameAs(events);
        verify(delegate).findEventsByOrderId(orderId);
    }
}
