package com.example.orderprocessing;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test that verifies the Spring application context loads successfully.
 * Redis auto-configurations are excluded and the connection factory is mocked
 * so no real Redis instance is required; H2 satisfies JPA via the test profile.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnableAutoConfiguration(exclude = {
        RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class
})
@ActiveProfiles("test")
class OrderProcessingApplicationTest {

    /** Mock Redis connection factory so CacheConfig can wire without a real Redis. */
    @MockBean
    RedisConnectionFactory redisConnectionFactory;

    /**
     * Asserts that the Spring application context starts without errors.
     */
    @Test
    void contextLoads() {
        // If the context fails to start, Spring Boot Test will throw before this line.
    }
}
