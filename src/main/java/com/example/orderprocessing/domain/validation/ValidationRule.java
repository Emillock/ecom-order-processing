package com.example.orderprocessing.domain.validation;

import com.example.orderprocessing.domain.model.Order;

/**
 * Contract for a single, self-contained validation rule that can be evaluated against an
 * {@link Order}.
 *
 * <p>Implementations live under {@code domain.validation.rules} and are registered with a
 * {@link ValidationChain}. Adding a new rule requires only a new class that implements this
 * interface — the chain itself is never modified (Open/Closed Principle, Requirement 14.2).
 *
 * <p>Each rule carries a stable {@link #ruleId()} that is embedded in the
 * {@link ValidationResult#fail(String, String)} payload so that callers can identify which
 * constraint was violated without parsing the human-readable message.
 *
 * <p>No Spring imports — this is a pure domain interface (Requirement 14.3).
 */
public interface ValidationRule {

    /**
     * Evaluates this rule against the given {@code order}.
     *
     * @param order the order to validate; must not be {@code null}
     * @return {@link ValidationResult#pass()} if the rule is satisfied, or
     *         {@link ValidationResult#fail(String, String)} carrying this rule's
     *         {@link #ruleId()} and a descriptive message if the rule is violated
     */
    ValidationResult validate(Order order);

    /**
     * Returns the stable identifier for this rule.
     *
     * <p>The identifier is used in failure payloads and audit logs so that downstream
     * consumers can react to specific rule violations without coupling to message text.
     * Identifiers should be lowercase, hyphen-separated strings (e.g.,
     * {@code "non-empty-items"}, {@code "positive-quantity"}).
     *
     * @return a non-null, non-empty stable rule identifier
     */
    String ruleId();
}
