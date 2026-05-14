package com.example.orderprocessing.domain.validation.rules;

import com.example.orderprocessing.domain.model.Order;
import com.example.orderprocessing.domain.model.OrderItem;
import com.example.orderprocessing.domain.validation.ValidationResult;
import com.example.orderprocessing.domain.validation.ValidationRule;

/**
 * Validation rule that ensures every line item in an {@link Order} has a quantity of at
 * least 1.
 *
 * <p>Although {@link OrderItem} enforces this invariant at construction time, this rule
 * provides an explicit, auditable check at the validation-chain layer so that any future
 * deserialization path that bypasses the constructor is still caught before the order
 * advances to pricing or fulfillment (Requirements 1.2, 2.1).
 *
 * <p>No Spring imports — this is a pure domain class (Requirement 14.3).
 */
public final class PositiveQuantityRule implements ValidationRule {

    /** Stable rule identifier embedded in failure payloads. */
    public static final String RULE_ID = "POSITIVE_QUANTITY";

    /** Human-readable failure message. */
    private static final String MESSAGE = "All order items must have a positive quantity";

    /**
     * Evaluates whether every item in the order has a quantity greater than zero.
     *
     * @param order the order to validate; must not be {@code null}
     * @return a passing result if all items have quantity &gt;= 1, or a failing result
     *         with rule id {@value #RULE_ID} if any item has quantity &lt; 1
     */
    @Override
    public ValidationResult validate(Order order) {
        boolean anyInvalid = order.getItems().stream()
                .anyMatch(item -> item.quantity() < 1);
        if (anyInvalid) {
            return ValidationResult.fail(RULE_ID, MESSAGE);
        }
        return ValidationResult.pass(RULE_ID);
    }

    /**
     * Returns the stable identifier for this rule.
     *
     * @return {@value #RULE_ID}
     */
    @Override
    public String ruleId() {
        return RULE_ID;
    }
}
