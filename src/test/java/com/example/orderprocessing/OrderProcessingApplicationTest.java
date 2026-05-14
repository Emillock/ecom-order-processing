package com.example.orderprocessing;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test that verifies the Spring application context loads successfully.
 * Redis auto-configurations are excluded so no real Redis instance is required;
 * H2 (test scope) satisfies JPA schema initialisation via the test profile.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnableAutoConfiguration(exclude = {
        RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class
})
@ActiveProfiles("test")
class OrderProcessingApplicationTest {

    /**
     * Asserts that the Spring application context starts without errors.
     * This is the minimum bar for the initial build skeleton (task 1.4).
     */
    @Test
    void contextLoads() {
        // If the context fails to start, Spring Boot Test will throw before this line.
    }
}
