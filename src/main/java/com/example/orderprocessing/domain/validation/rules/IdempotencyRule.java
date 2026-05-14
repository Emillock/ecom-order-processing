package com.example.orderprocessing.domain.validation.rules;

import com.example.orderprocessing.domain.model.Order;
import com.example.orderprocessing.domain.validation.ValidationResult;
import com.example.orderprocessing.domain.validation.ValidationRule;

/**
 * Validation rule that ensures the idempotency key, when present, is not blank.
 *
 * <p>A client may omit the idempotency key entirely (the {@link java.util.Optional} is
 * empty), in which case this rule passes. However, if the key is present its value must
 * be non-blank so that the deduplication store can use it as a meaningful lookup key
 * (Requirements 2.1, 2.3).
 *
 * <p>No Spring imports — this is a pure domain class (Requirement 14.3).
 */
public final class IdempotencyRule implements ValidationRule {

    /** Stable rule identifier embedded in failure payloads. */
    public static final String RULE_ID = "IDEMPOTENCY_KEY_FORMAT";

    /** Human-readable failure message. */
    private static final String MESSAGE = "Idempotency key must not be blank when provided";

    /**
     * Evaluates whether the idempotency key, if present, has a non-blank value.
     *
     * <p>The rule passes when:
     * <ul>
     *   <li>the idempotency key is absent ({@code Optional.empty()}), or</li>
     *   <li>the idempotency key is present and its {@code value} is non-blank.</li>
     * </ul>
     *
     * @param order the order to validate; must not be {@code null}
     * @return a passing result if the idempotency key is absent or non-blank, or a
     *         failing result with rule id {@value #RULE_ID} if the key is present but blank
     */
    @Override
    public ValidationResult validate(Order order) {
        return order.getIdempotencyKey()
                .filter(key -> key.value() == null || key.value().isBlank())
                .map(key -> ValidationResult.fail(RULE_ID, MESSAGE))
                .orElse(ValidationResult.pass(RULE_ID));
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
