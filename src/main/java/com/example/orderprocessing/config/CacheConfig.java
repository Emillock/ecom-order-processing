package com.example.orderprocessing.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Spring configuration for the Redis cache-aside layer.
 *
 * <p>Configures a {@link RedisTemplate}&lt;String, String&gt; that stores order JSON
 * under the versioned key schema {@code order:v1:{orderId}}, and exposes the
 * configured TTL so that {@code RedisOrderCache} can apply it on every write.
 *
 * <p>The TTL is read from {@code app.cache.order.ttl} (default {@code 5m}) so that
 * operators can tune cache lifetime without recompiling (Requirement 10.2).
 *
 * <p>Excluded from the {@code demo} profile — the demo uses an in-memory stub instead.
 */
@Configuration
@Profile("!demo")
public class CacheConfig {

    /**
     * Key prefix applied to every cached order entry.
     * Versioned so a future schema change can roll forward without a full cache flush.
     */
    public static final String ORDER_KEY_PREFIX = "order:v1:";

    private final Duration orderTtl;

    /**
     * Constructs a {@code CacheConfig} with the TTL sourced from application properties.
     *
     * @param orderTtl TTL for cached order entries; defaults to 5 minutes if not set
     */
    public CacheConfig(
            @Value("${app.cache.order.ttl:5m}") Duration orderTtl) {
        this.orderTtl = orderTtl;
    }

    /**
     * Returns the configured TTL for cached order entries.
     *
     * @return the order cache TTL as a {@link Duration}
     */
    public Duration getOrderTtl() {
        return orderTtl;
    }

    /**
     * Produces a {@link RedisTemplate}&lt;String, String&gt; bean used by the cache adapter.
     *
     * <p>Both keys and values are serialized as plain UTF-8 strings. Values are JSON
     * produced by the shared {@link ObjectMapper} (see {@link JacksonConfig}), keeping
     * the cache payload identical to the REST {@code OrderResponse} shape (Requirement 18).
     *
     * @param connectionFactory the Lettuce connection factory auto-configured by Spring Boot
     * @param objectMapper      the shared Jackson mapper (registered by {@link JacksonConfig})
     * @return a fully configured, thread-safe {@link RedisTemplate}
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper) {

        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // Keys and values are both plain UTF-8 strings (JSON for values)
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);

        template.setDefaultSerializer(stringSerializer);
        template.afterPropertiesSet();

        return template;
    }
}
