package com.example.orderprocessing.domain.validation;

import com.example.orderprocessing.domain.model.Order;

import java.util.List;

/**
 * Orchestrates an ordered sequence of {@link ValidationRule} instances against an {@link Order}.
 *
 * <p>Rules are supplied at construction time via a {@code List<ValidationRule>}, which is
 * typically injected by the Spring container. Adding a new rule requires only registering a new
 * bean — this class is never modified (Open/Closed Principle, Requirement 14.2).
 *
 * <p>Evaluation short-circuits on the first failing rule (Requirement 2.3). If all rules pass,
 * a synthetic passing result with {@code ruleId="ALL_PASSED"} is returned (Requirement 2.4).
 *
 * <p>No Spring imports — this is a pure domain class (Requirement 14.2).
 */
public final class ValidationChain {

    /** The ordered list of rules to evaluate. */
    private final List<ValidationRule> rules;

    /**
     * Constructs a {@code ValidationChain} with the given ordered list of rules.
     *
     * @param rules the validation rules to apply, in evaluation order; must not be {@code null}
     * @throws IllegalArgumentException if {@code rules} is {@code null}
     */
    public ValidationChain(List<ValidationRule> rules) {
        if (rules == null) {
            throw new IllegalArgumentException("rules must not be null");
        }
        this.rules = List.copyOf(rules);
    }

    /**
     * Validates the given {@code order} by running every registered rule in order.
     *
     * <p>Evaluation short-circuits on the first failing rule and returns that rule's
     * {@link ValidationResult} immediately (Requirement 2.3). If all rules pass, a synthetic
     * passing result with {@code ruleId="ALL_PASSED"} is returned (Requirement 2.4).
     *
     * @param order the order to validate; must not be {@code null}
     * @return the first failing {@link ValidationResult} if any rule fails, or a passing result
     *         with {@code ruleId="ALL_PASSED"} if every rule passes
     * @throws IllegalArgumentException if {@code order} is {@code null}
     */
    public ValidationResult validate(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("order must not be null");
        }
        for (ValidationRule rule : rules) {
            ValidationResult result = rule.validate(order);
            if (result.isFailed()) {
                return result;
            }
        }
        return ValidationResult.pass("ALL_PASSED");
    }

    /**
     * Returns the stable rule identifiers of all registered rules, in evaluation order.
     *
     * <p>Intended for diagnostics and observability — callers can inspect which rules are active
     * without triggering validation (Requirement 2.2).
     *
     * @return an unmodifiable list of rule IDs; never {@code null}
     */
    public List<String> getRuleIds() {
        return rules.stream()
                .map(ValidationRule::ruleId)
                .toList();
    }
}
