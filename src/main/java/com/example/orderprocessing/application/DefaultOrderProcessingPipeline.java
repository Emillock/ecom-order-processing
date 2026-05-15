package com.example.orderprocessing.application;

import com.example.orderprocessing.domain.notification.NotificationDispatcher;
import com.example.orderprocessing.domain.port.InventoryPort;
import com.example.orderprocessing.domain.port.PaymentPort;
import com.example.orderprocessing.domain.pricing.PricingEngine;
import com.example.orderprocessing.domain.validation.ValidationChain;
import org.springframework.stereotype.Component;

/**
 * Default concrete implementation of {@link OrderProcessingPipeline}.
 *
 * <p>Uses the {@code "default"} pricing profile for all orders. This is the production
 * pipeline bean wired by Spring; alternative profiles can be introduced by creating
 * additional subclasses or by making the profile configurable via a property.
 *
 * <p>Satisfies the Template Method pattern requirement (Requirement 13.3): the fixed
 * pipeline sequence is defined in the abstract parent; this class only supplies the
 * pricing profile name.
 */
@Component
public class DefaultOrderProcessingPipeline extends OrderProcessingPipeline {

    /**
     * Constructs the default pipeline with all required collaborators.
     *
     * @param validationChain        the chain of validation rules
     * @param pricingEngine          the pricing engine
     * @param inventoryPort          the inventory reservation port
     * @param paymentPort            the payment authorization port
     * @param notificationDispatcher the notification dispatcher
     */
    public DefaultOrderProcessingPipeline(
            ValidationChain validationChain,
            PricingEngine pricingEngine,
            InventoryPort inventoryPort,
            PaymentPort paymentPort,
            NotificationDispatcher notificationDispatcher) {
        super(validationChain, pricingEngine, inventoryPort, paymentPort, notificationDispatcher);
    }

    /**
     * Returns the pricing profile name used by this pipeline.
     *
     * @return {@code "default"}
     */
    @Override
    protected String pricingProfile() {
        return "default";
    }
}
