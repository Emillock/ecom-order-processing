package com.example.orderprocessing.unit.domain.validation;

import com.example.orderprocessing.domain.model.Money;
import com.example.orderprocessing.domain.model.Order;
import com.example.orderprocessing.domain.model.OrderBuilder;
import com.example.orderprocessing.domain.model.OrderId;
import com.example.orderprocessing.domain.model.OrderItem;
import com.example.orderprocessing.domain.model.Sku;
import com.example.orderprocessing.domain.validation.ValidationChain;
import com.example.orderprocessing.domain.validation.ValidationResult;
import com.example.orderprocessing.domain.validation.ValidationRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests verifying that {@link ValidationChain} honours the Open/Closed Principle:
 * a new {@link ValidationRule} can be registered at runtime and will be invoked by the
 * chain without any modification to the chain class itself.
 *
 * <p>Validates: Requirements 2.4, 14.2
 */
class ValidationChainExtensionTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Builds a minimal valid {@link Order} suitable for passing through the chain. */
    private static Order minimalOrder() {
        Currency usd = Currency.getInstance("USD");
        return new OrderBuilder()
                .id(new OrderId(UUID.randomUUID()))
                .item(new OrderItem(new Sku("SKU-001"), 1, Money.of("10.00", "USD")))
                .build();
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Custom rule added at runtime is invoked by the chain without modifying ValidationChain")
    void customRuleRegisteredAtRuntimeIsInvoked() {
        // Arrange — create a custom rule inline; ValidationChain is never modified
        AtomicBoolean ruleInvoked = new AtomicBoolean(false);

        ValidationRule customRule = new ValidationRule() {
            @Override
            public ValidationResult validate(Order order) {
                ruleInvoked.set(true);
                return ValidationResult.pass(ruleId());
            }

            @Override
            public String ruleId() {
                return "custom-runtime-rule";
            }
        };

        ValidationChain chain = new ValidationChain(List.of(customRule));
        Order order = minimalOrder();

        // Act
        ValidationResult result = chain.validate(order);

        // Assert — the custom rule was invoked and the chain reports success
        assertTrue(ruleInvoked.get(), "Custom rule registered at runtime must be invoked by the chain");
        assertTrue(result.isPassed(), "Chain should pass when the only rule passes");
    }

    @Test
    @DisplayName("Custom failing rule added at runtime causes chain to return its failure result")
    void customFailingRuleRegisteredAtRuntimeShortCircuitsChain() {
        // Arrange — a rule that always fails
        ValidationRule failingRule = new ValidationRule() {
            @Override
            public ValidationResult validate(Order order) {
                return ValidationResult.fail(ruleId(), "runtime rule violation");
            }

            @Override
            public String ruleId() {
                return "custom-failing-rule";
            }
        };

        ValidationChain chain = new ValidationChain(List.of(failingRule));
        Order order = minimalOrder();

        // Act
        ValidationResult result = chain.validate(order);

        // Assert — chain returns the custom rule's failure without any chain modification
        assertTrue(result.isFailed(), "Chain should fail when the custom rule fails");
        assertEquals("custom-failing-rule", result.getRuleId(),
                "Failure result must carry the custom rule's ruleId");
        assertEquals("runtime rule violation", result.getMessage(),
                "Failure result must carry the custom rule's message");
    }

    @Test
    @DisplayName("Custom rule is visible in getRuleIds() confirming it is registered in the chain")
    void customRuleAppearsInRuleIds() {
        // Arrange
        ValidationRule customRule = new ValidationRule() {
            @Override
            public ValidationResult validate(Order order) {
                return ValidationResult.pass(ruleId());
            }

            @Override
            public String ruleId() {
                return "observable-custom-rule";
            }
        };

        ValidationChain chain = new ValidationChain(List.of(customRule));

        // Act
        List<String> ruleIds = chain.getRuleIds();

        // Assert — the chain exposes the custom rule's id for diagnostics (Requirement 2.2)
        assertTrue(ruleIds.contains("observable-custom-rule"),
                "getRuleIds() must include the custom rule's ruleId");
    }

    @Test
    @DisplayName("Custom rule is invoked after existing rules in a mixed chain")
    void customRuleInvokedAfterExistingRulesInMixedChain() {
        // Arrange — two rules; track invocation order
        AtomicBoolean firstInvoked = new AtomicBoolean(false);
        AtomicBoolean secondInvoked = new AtomicBoolean(false);

        ValidationRule firstRule = new ValidationRule() {
            @Override
            public ValidationResult validate(Order order) {
                firstInvoked.set(true);
                return ValidationResult.pass(ruleId());
            }

            @Override
            public String ruleId() {
                return "first-rule";
            }
        };

        // Custom rule added at runtime — no modification to ValidationChain
        ValidationRule customRule = new ValidationRule() {
            @Override
            public ValidationResult validate(Order order) {
                secondInvoked.set(true);
                return ValidationResult.pass(ruleId());
            }

            @Override
            public String ruleId() {
                return "custom-second-rule";
            }
        };

        ValidationChain chain = new ValidationChain(List.of(firstRule, customRule));
        Order order = minimalOrder();

        // Act
        ValidationResult result = chain.validate(order);

        // Assert — both rules were invoked and the chain passed
        assertTrue(firstInvoked.get(), "First rule must be invoked");
        assertTrue(secondInvoked.get(), "Custom rule added at runtime must also be invoked");
        assertTrue(result.isPassed(), "Chain should pass when all rules pass");
        assertEquals("ALL_PASSED", result.getRuleId(),
                "Synthetic ALL_PASSED result expected when every rule passes");
    }
}
