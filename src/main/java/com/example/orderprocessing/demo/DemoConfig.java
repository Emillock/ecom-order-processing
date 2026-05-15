package com.example.orderprocessing.demo;

import com.example.orderprocessing.domain.model.IdempotencyKey;
import com.example.orderprocessing.domain.model.Order;
import com.example.orderprocessing.domain.model.OrderId;
import com.example.orderprocessing.domain.model.OrderItem;
import com.example.orderprocessing.domain.model.OrderStatus;
import com.example.orderprocessing.domain.model.OrderStatusEvent;
import com.example.orderprocessing.domain.port.AuthorizationResult;
import com.example.orderprocessing.domain.port.IdempotencyStore;
import com.example.orderprocessing.domain.port.InventoryPort;
import com.example.orderprocessing.domain.port.OrderCachePort;
import com.example.orderprocessing.domain.port.OrderQuery;
import com.example.orderprocessing.domain.port.OrderRepository;
import com.example.orderprocessing.domain.port.PaymentPort;
import com.example.orderprocessing.domain.port.ReservationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring configuration that provides in-memory stub implementations of all
 * infrastructure ports for the {@code demo} profile.
 *
 * <p>No real PostgreSQL, Redis, or external HTTP providers are required.
 * All beans are marked {@code @Primary} so they override the production
 * infrastructure beans when the {@code demo} profile is active.
 */
@Configuration
@Profile("demo")
public class DemoConfig {

    private static final Logger log = LoggerFactory.getLogger(DemoConfig.class);

    // =========================================================================
    // In-memory OrderRepository
    // =========================================================================

    /**
     * In-memory order store backed by a {@link ConcurrentHashMap}.
     * Satisfies the {@link OrderRepository} port for demo purposes.
     */
    @Bean
    @Primary
    public OrderRepository demoOrderRepository() {
        return new OrderRepository() {

            private final Map<OrderId, Order> orders = new ConcurrentHashMap<>();
            private final Map<OrderId, List<OrderStatusEvent>> events = new ConcurrentHashMap<>();

            @Override
            public Optional<Order> findById(OrderId id) {
                return Optional.ofNullable(orders.get(id));
            }

            @Override
            public Order save(Order order) {
                orders.put(order.getId(), order);
                return order;
            }

            @Override
            public void appendStatusEvent(OrderStatusEvent e) {
                events.computeIfAbsent(e.orderId(), k -> new ArrayList<>()).add(e);
            }

            @Override
            public Page<Order> search(OrderQuery query, Pageable pageable) {
                List<Order> all = new ArrayList<>(orders.values());
                if (query.status() != null) {
                    all = all.stream()
                            .filter(o -> o.getStatus() == query.status())
                            .toList();
                }
                int start = (int) pageable.getOffset();
                int end = Math.min(start + pageable.getPageSize(), all.size());
                List<Order> page = start >= all.size() ? List.of() : all.subList(start, end);
                return new PageImpl<>(page, pageable, all.size());
            }

            @Override
            public List<OrderStatusEvent> findEventsByOrderId(OrderId id) {
                return events.getOrDefault(id, List.of());
            }
        };
    }

    // =========================================================================
    // In-memory IdempotencyStore
    // =========================================================================

    /**
     * In-memory idempotency store backed by a {@link ConcurrentHashMap}.
     */
    @Bean
    @Primary
    public IdempotencyStore demoIdempotencyStore() {
        return new IdempotencyStore() {

            private final Map<String, OrderId> store = new ConcurrentHashMap<>();

            @Override
            public Optional<OrderId> findExisting(IdempotencyKey key) {
                return Optional.ofNullable(store.get(key.value()));
            }

            @Override
            public void register(IdempotencyKey key, OrderId id) {
                store.putIfAbsent(key.value(), id);
            }
        };
    }

    // =========================================================================
    // Always-succeeding InventoryPort stub
    // =========================================================================

    /**
     * Stub inventory adapter that always returns a successful reservation.
     * Logs reserve and release calls so they are visible in the demo output.
     */
    @Bean
    @Primary
    public InventoryPort demoInventoryPort() {
        return new InventoryPort() {

            @Override
            public ReservationResult reserve(OrderId id, List<OrderItem> items) {
                log.info("  [INVENTORY] Reserving {} item(s) for order {}",
                        items.size(), id.value());
                items.forEach(item ->
                        log.info("    - {} x{}", item.sku().value(), item.quantity()));
                return ReservationResult.success();
            }

            @Override
            public void release(OrderId id, List<OrderItem> items) {
                log.info("  [INVENTORY] Releasing reservation for order {} ({} item(s))",
                        id.value(), items.size());
            }
        };
    }

    // =========================================================================
    // Always-authorising PaymentPort stub
    // =========================================================================

    /**
     * Stub payment adapter that always returns an authorized result.
     * Logs authorize and void calls so they are visible in the demo output.
     */
    @Bean
    @Primary
    public PaymentPort demoPaymentPort() {
        return new PaymentPort() {

            @Override
            public AuthorizationResult authorize(OrderId id, com.example.orderprocessing.domain.model.Money grandTotal) {
                log.info("  [PAYMENT] Authorizing {} for order {}",
                        grandTotal.currency().getSymbol() + grandTotal.amount().toPlainString(),
                        id.value());
                return AuthorizationResult.authorized();
            }

            @Override
            public void voidAuthorization(OrderId id) {
                log.info("  [PAYMENT] Voiding authorization for order {}", id.value());
            }
        };
    }

    // =========================================================================
    // No-op OrderCachePort stub (cache always unavailable in demo)
    // =========================================================================

    /**
     * Stub cache port that reports itself as unavailable, so all reads fall through
     * to the in-memory repository. This keeps the demo simple — no Redis needed.
     */
    @Bean
    @Primary
    public OrderCachePort demoOrderCachePort() {
        return new OrderCachePort() {

            @Override
            public Optional<Order> get(OrderId id) {
                return Optional.empty();
            }

            @Override
            public void put(OrderId id, Order order) {
                // no-op
            }

            @Override
            public void evict(OrderId id) {
                // no-op
            }

            @Override
            public boolean isAvailable() {
                return false; // bypass cache in demo
            }
        };
    }
}
