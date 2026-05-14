package com.example.orderprocessing.infrastructure.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the external payment HTTP provider.
 *
 * <p>Bound from the {@code app.payment} prefix in {@code application.yml}.
 * The {@code baseUrl} property is the root URL of the payment service
 * (e.g., {@code http://payment-service:8080}).
 */
@ConfigurationProperties(prefix = "app.payment")
public class PaymentClientConfig {

    /** Base URL of the external payment provider. */
    private String baseUrl = "http://localhost:9002";

    /**
     * Returns the configured base URL for the payment HTTP client.
     *
     * @return the base URL; never {@code null}
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Sets the base URL for the payment HTTP client.
     *
     * @param baseUrl the base URL to use; must not be {@code null} or blank
     */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
