package com.example.orderprocessing.domain.validation;

/**
 * Represents the outcome of evaluating a single {@link ValidationRule} against an {@code Order}.
 *
 * <p>This is a sealed interface with two permitted implementations:
 * <ul>
 *   <li>{@link Pass} — the rule was satisfied; carries the stable {@code ruleId} of the rule
 *       that passed and a {@code null} message.</li>
 *   <li>{@link Fail} — the rule was violated; carries a stable {@code ruleId} and a human-readable
 *       {@code message}.</li>
 * </ul>
 *
 * <p>Use the static factories {@link #pass(String)} and {@link #fail(String, String)} to construct
 * instances. Callers can switch on the sealed type or use {@link #isPassed()} / {@link #isFailed()}
 * for simple boolean checks.
 *
 * <p>No Spring imports — this is a pure domain type (Requirements 2.1, 14.2).
 */
public sealed interface ValidationResult permits ValidationResult.Pass, ValidationResult.Fail {

    /**
     * Returns {@code true} if the validation rule passed.
     *
     * @return {@code true} for a {@link Pass} result, {@code false} for a {@link Fail} result
     */
    boolean isPassed();

    /**
     * Returns {@code true} if the validation rule failed.
     *
     * @return {@code true} for a {@link Fail} result, {@code false} for a {@link Pass} result
     */
    default boolean isFailed() {
        return !isPassed();
    }

    /**
     * Returns the stable identifier of the rule that produced this result.
     *
     * @return the rule ID; never {@code null}
     */
    String getRuleId();

    /**
     * Returns the human-readable failure message, or {@code null} for a passing result.
     *
     * @return the failure message, or {@code null} if the result is a pass
     */
    String getMessage();

    // -------------------------------------------------------------------------
    // Static factories
    // -------------------------------------------------------------------------

    /**
     * Creates a passing {@code ValidationResult} for the given rule.
     *
     * @param ruleId the stable identifier of the rule that passed; must not be {@code null}
     * @return a {@link Pass} instance carrying the given {@code ruleId}
     * @throws IllegalArgumentException if {@code ruleId} is {@code null}
     */
    static ValidationResult pass(String ruleId) {
        if (ruleId == null) {
            throw new IllegalArgumentException("ruleId must not be null");
        }
        return new Pass(ruleId);
    }

    /**
     * Creates a failing {@code ValidationResult} with a stable rule identifier and message.
     *
     * @param ruleId  the stable identifier of the rule that failed; must not be {@code null}
     * @param message a human-readable description of the failure; must not be {@code null}
     * @return a {@link Fail} instance carrying the given {@code ruleId} and {@code message}
     * @throws IllegalArgumentException if {@code ruleId} or {@code message} is {@code null}
     */
    static ValidationResult fail(String ruleId, String message) {
        if (ruleId == null) {
            throw new IllegalArgumentException("ruleId must not be null");
        }
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        return new Fail(ruleId, message);
    }

    // -------------------------------------------------------------------------
    // Permitted implementations
    // -------------------------------------------------------------------------

    /**
     * Record representing a passing validation result, carrying the stable rule identifier
     * of the rule that was satisfied.
     *
     * @param ruleId the stable identifier of the rule that passed
     */
    record Pass(String ruleId) implements ValidationResult {

        /**
         * Compact constructor that rejects a null ruleId.
         *
         * @throws IllegalArgumentException if {@code ruleId} is {@code null}
         */
        public Pass {
            if (ruleId == null) {
                throw new IllegalArgumentException("ruleId must not be null");
            }
        }

        /** {@inheritDoc} */
        @Override
        public boolean isPassed() {
            return true;
        }

        /** {@inheritDoc} */
        @Override
        public String getRuleId() {
            return ruleId;
        }

        /** {@inheritDoc} Returns {@code null} because there is no failure message. */
        @Override
        public String getMessage() {
            return null;
        }
    }

    /**
     * Record representing a failing validation result, carrying the stable rule identifier
     * and a human-readable message that explains why the rule was violated.
     *
     * @param ruleId  the stable identifier of the violated rule
     * @param message a human-readable description of the violation
     */
    record Fail(String ruleId, String message) implements ValidationResult {

        /**
         * Compact constructor that rejects null fields.
         *
         * @throws IllegalArgumentException if {@code ruleId} or {@code message} is {@code null}
         */
        public Fail {
            if (ruleId == null) {
                throw new IllegalArgumentException("ruleId must not be null");
            }
            if (message == null) {
                throw new IllegalArgumentException("message must not be null");
            }
        }

        /** {@inheritDoc} */
        @Override
        public boolean isPassed() {
            return false;
        }

        /** {@inheritDoc} */
        @Override
        public String getRuleId() {
            return ruleId;
        }

        /** {@inheritDoc} */
        @Override
        public String getMessage() {
            return message;
        }
    }
}
