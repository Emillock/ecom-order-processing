package com.example.orderprocessing.domain.port;

/**
 * Captures the outcome of a payment authorization attempt made via {@link PaymentPort}.
 *
 * <p>An {@code AuthorizationResult} is a sealed type with three possible variants:
 * <ul>
 *   <li>{@link Authorized} — the payment was authorized successfully.</li>
 *   <li>{@link Declined} — the payment provider explicitly declined the authorization,
 *       typically due to insufficient funds, card restrictions, or fraud rules.</li>
 *   <li>{@link Failed} — the authorization attempt failed for a technical or unexpected reason
 *       (e.g., network error, provider timeout).</li>
 * </ul>
 *
 * <p>Use the static factory methods ({@link #authorized()}, {@link #declined(String)},
 * {@link #failed(String)}) to construct instances, and {@link #isAuthorized()} to branch on
 * the outcome without pattern-matching boilerplate.
 */
public sealed interface AuthorizationResult permits
        AuthorizationResult.Authorized,
        AuthorizationResult.Declined,
        AuthorizationResult.Failed {

    // -------------------------------------------------------------------------
    // Static factory methods
    // -------------------------------------------------------------------------

    /**
     * Returns a result indicating that the payment was authorized successfully.
     *
     * @return an {@link Authorized} instance
     */
    static AuthorizationResult authorized() {
        return new Authorized();
    }

    /**
     * Returns a result indicating that the payment provider declined the authorization.
     *
     * @param reason a non-null, non-blank human-readable description of the decline reason
     * @return a {@link Declined} instance
     * @throws IllegalArgumentException if {@code reason} is null or blank
     */
    static AuthorizationResult declined(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be null or blank");
        }
        return new Declined(reason);
    }

    /**
     * Returns a result indicating that the authorization attempt failed for a technical reason.
     *
     * @param reason a non-null, non-blank human-readable description of the failure
     * @return a {@link Failed} instance
     * @throws IllegalArgumentException if {@code reason} is null or blank
     */
    static AuthorizationResult failed(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be null or blank");
        }
        return new Failed(reason);
    }

    // -------------------------------------------------------------------------
    // Query methods
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if and only if this result represents a successful authorization.
     *
     * @return {@code true} for {@link Authorized}, {@code false} for all other variants
     */
    default boolean isAuthorized() {
        return this instanceof Authorized;
    }

    /**
     * Returns the decline or failure reason, or {@code null} if this result is an
     * {@link Authorized} variant.
     *
     * <p>This method returns the reason for both {@link Declined} and {@link Failed} variants,
     * since callers typically need to surface the reason regardless of which non-success path
     * was taken.
     *
     * @return the reason string, or {@code null} for a successful authorization
     */
    default String getDeclineReason() {
        return switch (this) {
            case Declined d -> d.reason();
            case Failed f   -> f.reason();
            case Authorized ignored -> null;
        };
    }

    // -------------------------------------------------------------------------
    // Permitted implementations
    // -------------------------------------------------------------------------

    /**
     * Indicates that the payment was authorized successfully.
     */
    record Authorized() implements AuthorizationResult {}

    /**
     * Indicates that the payment provider explicitly declined the authorization.
     *
     * @param reason a human-readable description of the decline reason
     */
    record Declined(String reason) implements AuthorizationResult {}

    /**
     * Indicates that the authorization attempt failed for a technical or unexpected reason.
     *
     * @param reason a human-readable description of the failure
     */
    record Failed(String reason) implements AuthorizationResult {}
}
