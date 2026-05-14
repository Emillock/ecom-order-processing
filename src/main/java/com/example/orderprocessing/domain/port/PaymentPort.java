package com.example.orderprocessing.domain.port;

import com.example.orderprocessing.domain.model.Money;
import com.example.orderprocessing.domain.model.OrderId;

/**
 * Secondary port for the external payment provider.
 *
 * <p>Implementations (e.g., {@code HttpPaymentAdapter}) live in the infrastructure layer
 * and translate between the domain model and the external HTTP/JSON contract. The domain
 * and application layers depend only on this interface (Requirement 14.3, 13.5).
 *
 * <p>Implementations must be annotated with a Resilience4j circuit breaker named
 * {@code "payment"} so that failures past the configured threshold cause the breaker to
 * open and the pipeline to transition the order to {@code FAILED} with reason
 * {@code dependency_unavailable:payment} (Requirements 5.4, 5.5, 12.1).
 */
public interface PaymentPort {

    /**
     * Requests a payment authorization (pre-authorization / hold) for the given order.
     *
     * <p>A successful authorization holds the specified amount against the customer's
     * payment method. The hold is captured or voided in a subsequent step.
     *
     * @param id         the order identifier for which payment is being authorized; must
     *                   not be {@code null}
     * @param grandTotal the total amount to authorize; must not be {@code null}
     * @return an {@link AuthorizationResult} indicating approval or the decline reason;
     *         never {@code null}
     */
    AuthorizationResult authorize(OrderId id, Money grandTotal);

    /**
     * Voids a previously issued payment authorization for the given order.
     *
     * <p>This method is called during order cancellation or failure after a successful
     * {@link #authorize} call. Implementations should treat a void for an unknown or
     * already-voided authorization as a no-op.
     *
     * @param id the order identifier whose authorization should be voided; must not be
     *           {@code null}
     */
    void voidAuthorization(OrderId id);
}
