package com.example.orderprocessing.infrastructure.inventory;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the external inventory HTTP provider.
 *
 * <p>Bound from the {@code app.inventory} prefix in {@code application.yml}.
 * The {@code baseUrl} property is the root URL of the inventory service
 * (e.g., {@code http://inventory-service:8080}).
 */
@ConfigurationProperties(prefix = "app.inventory")
public class InventoryClientConfig {

    /** Base URL of the external inventory provider. */
    private String baseUrl = "http://localhost:9001";

    /**
     * Returns the configured base URL for the inventory HTTP client.
     *
     * @return the base URL; never {@code null}
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Sets the base URL for the inventory HTTP client.
     *
     * @param baseUrl the base URL to use; must not be {@code null} or blank
     */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
