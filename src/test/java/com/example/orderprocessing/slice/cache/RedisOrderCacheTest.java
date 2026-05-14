package com.example.orderprocessing.slice.cache;

import com.example.orderprocessing.config.CacheConfig;
import com.example.orderprocessing.domain.model.Money;
import com.example.orderprocessing.domain.model.Order;
import com.example.orderprocessing.domain.model.OrderBuilder;
import com.example.orderprocessing.domain.model.OrderId;
import com.example.orderprocessing.domain.model.OrderItem;
import com.example.orderprocessing.domain.model.OrderStatus;
import com.example.orderprocessing.domain.model.Sku;
import com.example.orderprocessing.infrastructure.cache.RedisOrderCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Slice tests for {@link RedisOrderCache} using an embedded Redis server.
 *
 * <p>Covers put/get/evict round-trips and TTL behavior (Requirements 10.1, 10.2).
 * Uses {@code it.ozimov:embedded-redis} to start a real Redis process on a test port
 * so that the full serialization/deserialization path is exercised without mocking.
 *
 * <p>The embedded Redis is started once for the entire test class ({@code @BeforeAll})
 * and stopped after all tests complete ({@code @AfterAll}) to minimize startup overhead.
 */
class RedisOrderCacheTest {

    /** Port for the embedded Redis instance — chosen to avoid conflicts with production (6379). */
    private static final int REDIS_TEST_PORT = 6370;

    private static RedisServer redisServer;
    private static LettuceConnectionFactory connectionFactory;

    private RedisTemplate<String, String> redisTemplate;
    private RedisOrderCache redisOrderCache;
    private CacheConfig cacheConfig;

    private static final Currency USD = Currency.getInstance("USD");

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Starts the embedded Redis server and creates a shared Lettuce connection factory
     * before any test in this class runs.
     */
    @BeforeAll
    static void startRedis() throws IOException {
        redisServer = new RedisServer(REDIS_TEST_PORT);
        redisServer.start();

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration("localhost", REDIS_TEST_PORT);
        connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
    }

    /**
     * Stops the embedded Redis server and destroys the connection factory after all
     * tests in this class have completed.
     */
    @AfterAll
    static void stopRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        if (redisServer != null) {
            redisServer.stop();
        }
    }

    /**
     * Builds a fresh {@link RedisOrderCache} before each test and flushes all keys
     * from the embedded Redis so tests are isolated.
     */
    @BeforeEach
    void setUp() {
        // Build a RedisTemplate backed by the embedded server
        redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        redisTemplate.setKeySerializer(stringSerializer);
        redisTemplate.setValueSerializer(stringSerializer);
        redisTemplate.setHashKeySerializer(stringSerializer);
        redisTemplate.setHashValueSerializer(stringSerializer);
        redisTemplate.setDefaultSerializer(stringSerializer);
        redisTemplate.afterPropertiesSet();

        // Flush all keys to ensure test isolation
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();

        // Configure a short TTL (2 seconds) so TTL expiry tests run quickly
        cacheConfig = new CacheConfig(Duration.ofSeconds(2));

        // Use a real ObjectMapper with JavaTimeModule for Instant serialization
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Circuit breaker registry with a CLOSED breaker so isAvailable() returns true
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        CircuitBreaker breaker = registry.circuitBreaker("cache");
        // Breaker starts CLOSED by default — no manipulation needed

        redisOrderCache = new RedisOrderCache(redisTemplate, objectMapper, cacheConfig, registry);
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /**
     * Builds a minimal valid {@link Order} for use in tests.
     *
     * @param id the order identifier to use
     * @return a fully constructed {@link Order} in CREATED status
     */
    private Order buildOrder(OrderId id) {
        return new OrderBuilder()
                .id(id)
                .items(List.of(new OrderItem(new Sku("SKU-001"), 2,
                        new Money(new BigDecimal("10.00"), USD))))
                .status(OrderStatus.CREATED)
                .subtotal(new Money(new BigDecimal("20.00"), USD))
                .discountTotal(new Money(BigDecimal.ZERO, USD))
                .taxTotal(new Money(new BigDecimal("2.00"), USD))
                .shippingTotal(new Money(new BigDecimal("5.00"), USD))
                .grandTotal(new Money(new BigDecimal("27.00"), USD))
                .build();
    }

    // -------------------------------------------------------------------------
    // put / get round-trip
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("get returns empty on cache miss")
    void get_returnEmpty_onCacheMiss() {
        OrderId id = new OrderId(UUID.randomUUID());

        Optional<Order> result = redisOrderCache.get(id);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("put then get returns the same order (round-trip)")
    void put_thenGet_returnsOrder() {
        OrderId id = new OrderId(UUID.randomUUID());
        Order order = buildOrder(id);

        redisOrderCache.put(id, order);
        Optional<Order> result = redisOrderCache.get(id);

        assertThat(result).isPresent();
        Order retrieved = result.get();
        assertThat(retrieved.getId()).isEqualTo(order.getId());
        assertThat(retrieved.getStatus()).isEqualTo(order.getStatus());
        assertThat(retrieved.getGrandTotal().amount())
                .isEqualByComparingTo(order.getGrandTotal().amount());
        assertThat(retrieved.getItems()).hasSize(order.getItems().size());
    }

    @Test
    @DisplayName("put is idempotent: repeated puts with same state leave cache unchanged")
    void put_isIdempotent_repeatedPutsWithSameState() {
        OrderId id = new OrderId(UUID.randomUUID());
        Order order = buildOrder(id);

        redisOrderCache.put(id, order);
        redisOrderCache.put(id, order);
        redisOrderCache.put(id, order);

        Optional<Order> result = redisOrderCache.get(id);
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(id);
    }

    @Test
    @DisplayName("put overwrites stale entry when order state changes")
    void put_overwritesStaleEntry_whenOrderStateChanges() {
        OrderId id = new OrderId(UUID.randomUUID());
        Order created = buildOrder(id);
        Order validated = created.withStatus(OrderStatus.VALIDATED);

        redisOrderCache.put(id, created);
        redisOrderCache.put(id, validated);

        Optional<Order> result = redisOrderCache.get(id);
        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(OrderStatus.VALIDATED);
    }

    // -------------------------------------------------------------------------
    // evict
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("evict removes the cached entry so subsequent get returns empty")
    void evict_removesEntry() {
        OrderId id = new OrderId(UUID.randomUUID());
        Order order = buildOrder(id);

        redisOrderCache.put(id, order);
        assertThat(redisOrderCache.get(id)).isPresent();

        redisOrderCache.evict(id);

        assertThat(redisOrderCache.get(id)).isEmpty();
    }

    @Test
    @DisplayName("evict on non-existent key is a no-op (does not throw)")
    void evict_nonExistentKey_isNoOp() {
        OrderId id = new OrderId(UUID.randomUUID());

        // Should not throw
        redisOrderCache.evict(id);

        assertThat(redisOrderCache.get(id)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // TTL behavior
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("cached entry expires after the configured TTL")
    void cachedEntry_expiresAfterTtl() throws InterruptedException {
        // TTL is set to 2 seconds in setUp(); wait 3 seconds for expiry
        OrderId id = new OrderId(UUID.randomUUID());
        Order order = buildOrder(id);

        redisOrderCache.put(id, order);
        assertThat(redisOrderCache.get(id)).isPresent();

        // Wait for TTL to expire
        TimeUnit.SECONDS.sleep(3);

        assertThat(redisOrderCache.get(id)).isEmpty();
    }

    @Test
    @DisplayName("TTL key is set in Redis after put")
    void put_setsTtlOnKey() {
        OrderId id = new OrderId(UUID.randomUUID());
        Order order = buildOrder(id);

        redisOrderCache.put(id, order);

        String key = CacheConfig.ORDER_KEY_PREFIX + id.value().toString();
        Long ttlSeconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);

        // TTL should be positive and <= configured 2 seconds
        assertThat(ttlSeconds).isNotNull().isPositive().isLessThanOrEqualTo(2L);
    }

    // -------------------------------------------------------------------------
    // isAvailable
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("isAvailable returns true when circuit breaker is CLOSED and Redis is reachable")
    void isAvailable_returnsTrue_whenBreakerClosedAndRedisReachable() {
        assertThat(redisOrderCache.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("isAvailable returns false when circuit breaker is OPEN")
    void isAvailable_returnsFalse_whenBreakerOpen() {
        // Build a registry with a breaker forced to OPEN state
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        CircuitBreaker breaker = registry.circuitBreaker("cache");
        breaker.transitionToOpenState();

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        RedisOrderCache cacheWithOpenBreaker =
                new RedisOrderCache(redisTemplate, objectMapper, cacheConfig, registry);

        assertThat(cacheWithOpenBreaker.isAvailable()).isFalse();
    }
}
