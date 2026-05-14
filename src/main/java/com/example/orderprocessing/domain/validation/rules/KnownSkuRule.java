package com.example.orderprocessing.domain.validation.rules;

import com.example.orderprocessing.domain.model.Order;
import com.example.orderprocessing.domain.validation.ValidationResult;
import com.example.orderprocessing.domain.validation.ValidationRule;

/**
 * Validation rule that ensures every line item in an {@link Order} carries a non-null,
 * non-blank SKU value.
 *
 * <p>A blank or absent SKU cannot be looked up in the inventory system, so this rule
 * prevents such orders from advancing to the reservation stage (Requirements 2.1, 2.3).
 *
 * <p>No Spring imports — this is a pure domain class (Requirement 14.3).
 */
public final class KnownSkuRule implements ValidationRule {

    /** Stable rule identifier embedded in failure payloads. */
    public static final String RULE_ID = "KNOWN_SKU";

    /** Human-readable failure message. */
    private static final String MESSAGE = "All order items must have a valid SKU";

    /**
     * Evaluates whether every item in the order has a non-null, non-blank SKU.
     *
     * @param order the order to validate; must not be {@code null}
     * @return a passing result if all items have a valid SKU, or a failing result
     *         with rule id {@value #RULE_ID} if any item has a {@code null} or blank SKU
     */
    @Override
    public ValidationResult validate(Order order) {
        boolean anyInvalid = order.getItems().stream()
                .anyMatch(item -> item.sku() == null
                        || item.sku().value() == null
                        || item.sku().value().isBlank());
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
