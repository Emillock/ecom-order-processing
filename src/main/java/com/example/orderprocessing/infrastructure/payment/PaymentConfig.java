package com.example.orderprocessing.infrastructure.payment;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration that activates {@link PaymentClientConfig} as a
 * {@code @ConfigurationProperties} bean, binding the {@code app.payment} prefix
 * from {@code application.yml}.
 */
@Configuration
@EnableConfigurationProperties(PaymentClientConfig.class)
public class PaymentConfig {
}
