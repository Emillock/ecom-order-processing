package com.example.orderprocessing.config;

import com.example.orderprocessing.domain.notification.NotificationDispatcher;
import com.example.orderprocessing.domain.notification.OrderEventListener;
import com.example.orderprocessing.domain.pricing.PricingEngine;
import com.example.orderprocessing.domain.pricing.PricingStrategyChainFactory;
import com.example.orderprocessing.domain.validation.ValidationChain;
import com.example.orderprocessing.domain.validation.ValidationRule;
import com.example.orderprocessing.domain.validation.rules.IdempotencyRule;
import com.example.orderprocessing.domain.validation.rules.KnownSkuRule;
import com.example.orderprocessing.domain.validation.rules.NonEmptyItemsRule;
import com.example.orderprocessing.domain.validation.rules.PositiveQuantityRule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Spring configuration that wires pure-domain objects as Spring beans.
 *
 * <p>Domain classes ({@link ValidationChain}, {@link PricingEngine},
 * {@link NotificationDispatcher}) have no Spring imports by design (Requirement 14.3).
 * This configuration class acts as the bridge between the Spring container and the
 * domain layer, satisfying the Dependency Inversion Principle without polluting the
 * domain with framework annotations.
 *
 * <p>This class is excluded from JaCoCo coverage checks because it contains only
 * Spring wiring with no branching business logic.
 */
@Configuration
public class ApplicationConfig {

    /**
     * Registers the {@link NonEmptyItemsRule} validation rule bean.
     *
     * @return a new {@link NonEmptyItemsRule}
     */
    @Bean
    public ValidationRule nonEmptyItemsRule() {
        return new NonEmptyItemsRule();
    }

    /**
     * Registers the {@link PositiveQuantityRule} validation rule bean.
     *
     * @return a new {@link PositiveQuantityRule}
     */
    @Bean
    public ValidationRule positiveQuantityRule() {
        return new PositiveQuantityRule();
    }

    /**
     * Registers the {@link KnownSkuRule} validation rule bean.
     *
     * @return a new {@link KnownSkuRule}
     */
    @Bean
    public ValidationRule knownSkuRule() {
        return new KnownSkuRule();
    }

    /**
     * Registers the {@link IdempotencyRule} validation rule bean.
     *
     * @return a new {@link IdempotencyRule}
     */
    @Bean
    public ValidationRule idempotencyRule() {
        return new IdempotencyRule();
    }

    /**
     * Creates the {@link ValidationChain} bean, injecting all registered
     * {@link ValidationRule} beans in the order Spring discovers them.
     *
     * <p>New validation rules are added by registering a new {@code ValidationRule} bean;
     * this method is never modified (Open/Closed Principle, Requirement 14.2).
     *
     * @param rules all {@link ValidationRule} beans in the application context
     * @return a configured {@link ValidationChain}
     */
    @Bean
    public ValidationChain validationChain(List<ValidationRule> rules) {
        return new ValidationChain(rules);
    }

    /**
     * Creates the {@link PricingStrategyChainFactory} bean.
     *
     * @return a new {@link PricingStrategyChainFactory}
     */
    @Bean
    public PricingStrategyChainFactory pricingStrategyChainFactory() {
        return new PricingStrategyChainFactory();
    }

    /**
     * Creates the {@link PricingEngine} bean, injecting the chain factory.
     *
     * @param chainFactory the factory used to build strategy chains by profile name
     * @return a configured {@link PricingEngine}
     */
    @Bean
    public PricingEngine pricingEngine(PricingStrategyChainFactory chainFactory) {
        return new PricingEngine(chainFactory);
    }

    /**
     * Creates the {@link NotificationDispatcher} bean, injecting all registered
     * {@link OrderEventListener} beans.
     *
     * <p>New notification channels are added by registering a new {@code OrderEventListener}
     * bean; this method is never modified (Open/Closed Principle, Requirement 14.2).
     *
     * @param listeners all {@link OrderEventListener} beans in the application context
     * @return a configured {@link NotificationDispatcher}
     */
    @Bean
    public NotificationDispatcher notificationDispatcher(List<OrderEventListener> listeners) {
        return new NotificationDispatcher(listeners);
    }
}
