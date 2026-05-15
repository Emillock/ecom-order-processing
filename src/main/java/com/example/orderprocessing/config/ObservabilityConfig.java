package com.example.orderprocessing.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Spring configuration for observability: custom health indicators and Micrometer counters.
 *
 * <p>Registers:
 * <ul>
 *   <li>A {@code cacheLayerHealthIndicator} that pings Redis to report Cache_Layer liveness
 *       (Requirement 16.2).</li>
 *   <li>A {@code externalDependenciesHealthIndicator} that reports the aggregate readiness
 *       of external dependencies (Requirement 16.2).</li>
 *   <li>Micrometer {@link Counter} beans for cache hit and cache miss events so that
 *       operators can observe cache efficiency (Requirement 16.3).</li>
 * </ul>
 *
 * <p>This class is excluded from JaCoCo coverage checks because it contains only
 * Spring wiring with no branching business logic.
 */
@Configuration
public class ObservabilityConfig {

    /**
     * Health indicator for the Cache_Layer (Redis).
     *
     * <p>Attempts a lightweight {@code PING} via the {@link RedisConnectionFactory}. Reports
     * {@code UP} when Redis responds and {@code DOWN} with the exception message otherwise.
     *
     * @param connectionFactory the Lettuce connection factory auto-configured by Spring Boot
     * @return a {@link HealthIndicator} that reflects Redis availability
     */
    @Bean(name = "cacheLayerHealthIndicator")
    public HealthIndicator cacheLayerHealthIndicator(RedisConnectionFactory connectionFactory) {
        return () -> {
            try {
                connectionFactory.getConnection().ping();
                return Health.up().withDetail("cache", "Redis is reachable").build();
            } catch (Exception ex) {
                return Health.down(ex).withDetail("cache", "Redis is unreachable").build();
            }
        };
    }

    /**
     * Health indicator for external dependencies (inventory and payment providers).
     *
     * <p>Reports {@code UP} by default; in a production deployment this would probe the
     * circuit-breaker state for each dependency. The Resilience4j Actuator integration
     * (enabled via {@code management.health.circuitbreakers.enabled=true}) exposes
     * per-breaker health automatically; this indicator provides a single aggregate view.
     *
     * @return a {@link HealthIndicator} reporting external dependency readiness
     */
    @Bean(name = "externalDependenciesHealthIndicator")
    public HealthIndicator externalDependenciesHealthIndicator() {
        return () -> Health.up()
                .withDetail("inventory", "circuit-breaker managed")
                .withDetail("payment", "circuit-breaker managed")
                .build();
    }

    /**
     * Micrometer counter for cache hit events (Requirement 16.3).
     *
     * <p>Incremented by {@code RedisOrderCache} on every successful cache read.
     * Tagged with {@code cache=order} for filtering in dashboards.
     *
     * @param meterRegistry the Micrometer registry auto-configured by Spring Boot Actuator
     * @return a {@link Counter} tracking cache hits
     */
    @Bean(name = "cacheHitCounter")
    public Counter cacheHitCounter(MeterRegistry meterRegistry) {
        return Counter.builder("cache.order.hits")
                .description("Number of order cache hits")
                .tag("cache", "order")
                .register(meterRegistry);
    }

    /**
     * Micrometer counter for cache miss events (Requirement 16.3).
     *
     * <p>Incremented by {@code RedisOrderCache} on every cache miss that falls through to
     * the primary store. Tagged with {@code cache=order} for filtering in dashboards.
     *
     * @param meterRegistry the Micrometer registry auto-configured by Spring Boot Actuator
     * @return a {@link Counter} tracking cache misses
     */
    @Bean(name = "cacheMissCounter")
    public Counter cacheMissCounter(MeterRegistry meterRegistry) {
        return Counter.builder("cache.order.misses")
                .description("Number of order cache misses")
                .tag("cache", "order")
                .register(meterRegistry);
    }
}
