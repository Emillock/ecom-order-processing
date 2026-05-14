package com.example.orderprocessing.infrastructure.inventory;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration that activates {@link InventoryClientConfig} as a
 * {@code @ConfigurationProperties} bean, binding the {@code app.inventory} prefix
 * from {@code application.yml}.
 */
@Configuration
@EnableConfigurationProperties(InventoryClientConfig.class)
public class InventoryConfig {
}
