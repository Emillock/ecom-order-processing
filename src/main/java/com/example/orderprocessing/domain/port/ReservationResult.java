package com.example.orderprocessing.domain.port;

import com.example.orderprocessing.domain.model.Sku;

import java.util.List;
import java.util.Objects;

/**
 * Captures the outcome of an inventory reservation attempt made via {@link InventoryPort}.
 *
 * <p>A {@code ReservationResult} is a sealed type with three possible variants:
 * <ul>
 *   <li>{@link Success} — all requested SKUs were reserved successfully.</li>
 *   <li>{@link OutOfStock} — one or more SKUs could not be reserved due to insufficient stock.</li>
 *   <li>{@link Failed} — the reservation attempt failed for a technical or unexpected reason.</li>
 * </ul>
 *
 * <p>Use the static factory methods ({@link #success()}, {@link #outOfStock(List)},
 * {@link #failed(String)}) to construct instances, and {@link #isSuccess()} to branch on the
 * outcome without pattern-matching boilerplate.
 */
public sealed interface ReservationResult permits
        ReservationResult.Success,
        ReservationResult.OutOfStock,
        ReservationResult.Failed {

    // -------------------------------------------------------------------------
    // Static factory methods
    // -------------------------------------------------------------------------

    /**
     * Returns a result indicating that all requested SKUs were reserved successfully.
     *
     * @return a {@link Success} instance
     */
    static ReservationResult success() {
        return new Success();
    }

    /**
     * Returns a result indicating that one or more SKUs are unavailable.
     *
     * @param unavailableSkus the non-null, non-empty list of SKUs that could not be reserved
     * @return an {@link OutOfStock} instance
     * @throws IllegalArgumentException if {@code unavailableSkus} is null or empty
     */
    static ReservationResult outOfStock(List<Sku> unavailableSkus) {
        Objects.requireNonNull(unavailableSkus, "unavailableSkus must not be null");
        if (unavailableSkus.isEmpty()) {
            throw new IllegalArgumentException("unavailableSkus must not be empty for an out-of-stock result");
        }
        return new OutOfStock(List.copyOf(unavailableSkus));
    }

    /**
     * Returns a result indicating that the reservation failed for a technical reason.
     *
     * @param reason a non-null, non-blank human-readable description of the failure
     * @return a {@link Failed} instance
     * @throws IllegalArgumentException if {@code reason} is null or blank
     */
    static ReservationResult failed(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be null or blank");
        }
        return new Failed(reason);
    }

    // -------------------------------------------------------------------------
    // Query methods
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if and only if this result represents a successful reservation.
     *
     * @return {@code true} for {@link Success}, {@code false} for all other variants
     */
    default boolean isSuccess() {
        return this instanceof Success;
    }

    /**
     * Returns the list of SKUs that could not be reserved, or an empty list if this result
     * is not an {@link OutOfStock} variant.
     *
     * @return an unmodifiable list of unavailable {@link Sku} values; never {@code null}
     */
    default List<Sku> getUnavailableSkus() {
        return this instanceof OutOfStock o ? o.unavailableSkus() : List.of();
    }

    /**
     * Returns the failure reason, or {@code null} if this result is not a {@link Failed} variant.
     *
     * @return the failure reason string, or {@code null}
     */
    default String getReason() {
        return this instanceof Failed f ? f.reason() : null;
    }

    // -------------------------------------------------------------------------
    // Permitted implementations
    // -------------------------------------------------------------------------

    /**
     * Indicates that all requested SKUs were reserved successfully.
     */
    record Success() implements ReservationResult {}

    /**
     * Indicates that one or more SKUs are out of stock.
     *
     * @param unavailableSkus the unmodifiable list of SKUs that could not be reserved
     */
    record OutOfStock(List<Sku> unavailableSkus) implements ReservationResult {}

    /**
     * Indicates that the reservation attempt failed for a technical or unexpected reason.
     *
     * @param reason a human-readable description of the failure
     */
    record Failed(String reason) implements ReservationResult {}
}
