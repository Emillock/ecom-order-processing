package com.example.orderprocessing.domain.validation.rules;

import com.example.orderprocessing.domain.model.Order;
import com.example.orderprocessing.domain.validation.ValidationResult;
import com.example.orderprocessing.domain.validation.ValidationRule;

/**
 * Validation rule that ensures an {@link Order} contains at least one line item.
 *
 * <p>An order with a {@code null} or empty items list cannot be priced, reserved, or
 * fulfilled, so this rule acts as the first gate in the validation chain
 * (Requirements 1.2, 2.1).
 *
 * <p>No Spring imports — this is a pure domain class (Requirement 14.3).
 */
public final class NonEmptyItemsRule implements ValidationRule {

    /** Stable rule identifier embedded in failure payloads. */
    public static final String RULE_ID = "NON_EMPTY_ITEMS";

    /** Human-readable failure message. */
    private static final String MESSAGE = "Order must contain at least one item";

    /**
     * Evaluates whether the order has at least one item.
     *
     * @param order the order to validate; must not be {@code null}
     * @return a passing result if the order has one or more items, or a failing result
     *         with rule id {@value #RULE_ID} otherwise
     */
    @Override
    public ValidationResult validate(Order order) {
        if (order.getItems() == null || order.getItems().isEmpty()) {
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
