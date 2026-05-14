package com.example.orderprocessing.infrastructure.cache;

import com.example.orderprocessing.config.CacheConfig;
import com.example.orderprocessing.domain.model.Order;
import com.example.orderprocessing.domain.model.OrderId;
import com.example.orderprocessing.domain.port.OrderCachePort;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Redis-backed implementation of {@link OrderCachePort}.
 *
 * <p>Stores serialized {@link Order} instances under the versioned key schema
 * {@code order:v1:{orderId}} using a {@link RedisTemplate}&lt;String, String&gt;.
 * Values are JSON-encoded via the shared {@link ObjectMapper} using the flat
 * {@link com.example.orderprocessing.property.serialization.OrderDto} shape so
 * that the cache payload is identical to the REST {@code OrderResponse}
 * (Requirement 18).
 *
 * <p>Every write applies the TTL configured in {@link CacheConfig#getOrderTtl()}
 * so that stale entries expire automatically (Requirement 10.2).
 *
 * <p>{@link #isAvailable()} returns {@code false} when the {@code "cache"}
 * Resilience4j circuit breaker is in the {@link CircuitBreaker.State#OPEN} state,
 * allowing the {@code CachingOrderRepository} decorator to bypass Redis and fall
 * back to the primary store (Requirements 10.3, 12.3, 14.2).
 */
@Component
public class RedisOrderCache implements OrderCachePort {

    private static final Logger log = LoggerFactory.getLogger(RedisOrderCache.class);

    /** Name of the Resilience4j circuit-breaker instance that guards the cache. */
    private static final String CACHE_BREAKER_NAME = "cache";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final CacheConfig cacheConfig;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    /**
     * Constructs a {@code RedisOrderCache} with all required collaborators.
     *
     * @param redisTemplate          the Redis template used for key/value operations
     * @param objectMapper           the shared Jackson mapper for JSON serialization
     * @param cacheConfig            provides the key prefix and TTL configuration
     * @param circuitBreakerRegistry the Resilience4j registry used to inspect the
     *                               {@code "cache"} circuit-breaker state
     */
    public RedisOrderCache(
            RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper,
            CacheConfig cacheConfig,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheConfig = cacheConfig;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    // -------------------------------------------------------------------------
    // OrderCachePort implementation
    // -------------------------------------------------------------------------

    /**
     * Retrieves a cached {@link Order} by its identifier.
     *
     * <p>Returns {@link Optional#empty()} on a cache miss, a deserialization error,
     * or any Redis connectivity failure. Errors are logged at WARN level and swallowed
     * so that callers can fall back to the primary store.
     *
     * @param id the order identifier; must not be {@code null}
     * @return an {@link Optional} containing the cached order, or empty on miss/error
     */
    @Override
    public Optional<Order> get(OrderId id) {
        String key = buildKey(id);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            OrderDto dto = objectMapper.readValue(json, OrderDto.class);
            return Optional.of(dto.toDomain());
        } catch (Exception ex) {
            log.warn("Cache GET failed for key={}: {}", key, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Stores an {@link Order} in the cache under the given identifier with the
     * configured TTL. This operation is idempotent: calling {@code put} with the
     * same {@code id} and an observably identical {@code order} leaves the cache
     * in the same state as a single call (Requirement 19.1).
     *
     * <p>Errors are logged at WARN level and swallowed so that a cache write
     * failure does not propagate to the caller.
     *
     * @param id    the order identifier; must not be {@code null}
     * @param order the order to cache; must not be {@code null}
     */
    @Override
    public void put(OrderId id, Order order) {
        String key = buildKey(id);
        try {
            String json = objectMapper.writeValueAsString(OrderDto.from(order));
            redisTemplate.opsForValue().set(key, json, cacheConfig.getOrderTtl());
        } catch (Exception ex) {
            log.warn("Cache PUT failed for key={}: {}", key, ex.getMessage());
        }
    }

    /**
     * Removes the cached entry for the given order identifier, if present.
     *
     * <p>This method is a no-op when no entry exists for {@code id}. Errors are
     * logged at WARN level and swallowed.
     *
     * @param id the order identifier whose cache entry should be removed; must not be
     *           {@code null}
     */
    @Override
    public void evict(OrderId id) {
        String key = buildKey(id);
        try {
            redisTemplate.delete(key);
        } catch (Exception ex) {
            log.warn("Cache EVICT failed for key={}: {}", key, ex.getMessage());
        }
    }

    /**
     * Returns {@code true} when the cache is reachable and the {@code "cache"}
     * Resilience4j circuit breaker is not in the {@link CircuitBreaker.State#OPEN} state.
     *
     * <p>When the breaker is OPEN, this method returns {@code false} immediately
     * without attempting a Redis ping, allowing the {@code CachingOrderRepository}
     * decorator to bypass the cache and record a {@code cache_degraded} event
     * (Requirements 10.3, 14.2).
     *
     * @return {@code true} if the cache is available; {@code false} if the circuit
     *         breaker is OPEN or the cache is otherwise unreachable
     */
    @Override
    public boolean isAvailable() {
        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker(CACHE_BREAKER_NAME);
        if (breaker.getState() == CircuitBreaker.State.OPEN) {
            return false;
        }
        try {
            // Perform a lightweight connectivity check
            redisTemplate.getConnectionFactory().getConnection().ping();
            return true;
        } catch (Exception ex) {
            log.warn("Cache availability check failed: {}", ex.getMessage());
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Builds the Redis key for the given order identifier using the versioned prefix.
     *
     * @param id the order identifier; must not be {@code null}
     * @return the fully-qualified Redis key string
     */
    private String buildKey(OrderId id) {
        return CacheConfig.ORDER_KEY_PREFIX + id.value().toString();
    }
}
