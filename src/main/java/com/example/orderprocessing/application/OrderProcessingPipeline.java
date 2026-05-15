package com.example.orderprocessing.application;

import com.example.orderprocessing.domain.lifecycle.OrderStateMachine;
import com.example.orderprocessing.domain.model.Order;
import com.example.orderprocessing.domain.model.OrderStatus;
import com.example.orderprocessing.domain.model.OrderStatusEvent;
import com.example.orderprocessing.domain.notification.NotificationDispatcher;
import com.example.orderprocessing.domain.port.AuthorizationResult;
import com.example.orderprocessing.domain.port.InventoryPort;
import com.example.orderprocessing.domain.port.PaymentPort;
import com.example.orderprocessing.domain.port.ReservationResult;
import com.example.orderprocessing.domain.pricing.PricingEngine;
import com.example.orderprocessing.domain.validation.ValidationChain;
import com.example.orderprocessing.domain.validation.ValidationResult;

import java.time.Instant;

/**
 * Template Method implementation that defines the fixed order-processing pipeline sequence.
 *
 * <p>The {@link #run(Order)} method is {@code final} and enforces the invariant stage order:
 * <ol>
 *   <li>{@link #validate(Order)} — runs the {@link ValidationChain}; transitions to VALIDATED
 *       or FAILED.</li>
 *   <li>{@link #price(Order)} — applies the {@link PricingEngine}; transitions to PRICED or
 *       FAILED.</li>
 *   <li>{@link #reserve(Order)} — calls {@link InventoryPort#reserve}; transitions to RESERVED
 *       or FAILED.</li>
 *   <li>{@link #pay(Order)} — calls {@link PaymentPort#authorize}; transitions to CONFIRMED or
 *       FAILED.</li>
 *   <li>{@link #notify(Order)} — dispatches an {@link OrderStatusEvent} via
 *       {@link NotificationDispatcher}.</li>
 * </ol>
 *
 * <p>Each stage method is {@code protected} so that subclasses (or test doubles) can override
 * individual stages without altering the pipeline shape (Template Method pattern, Requirement
 * 13.3). The class itself is abstract so that the pricing profile and any additional
 * configuration can be supplied by the concrete subclass.
 *
 * <p>This class has no Spring imports; it is a pure application-layer object wired by the
 * Spring container via constructor injection (Requirement 14.3).
 *
 * <p>Satisfies Requirements 2.1, 3.1, 4.1, 5.1, 8.1, and 13.3.
 */
public abstract class OrderProcessingPipeline {

    /** Actor label used when recording system-initiated status events. */
    protected static final String SYSTEM_ACTOR = "system";

    private final ValidationChain validationChain;
    private final PricingEngine pricingEngine;
    private final InventoryPort inventoryPort;
    private final PaymentPort paymentPort;
    private final NotificationDispatcher notificationDispatcher;

    /**
     * Constructs an {@code OrderProcessingPipeline} with all required collaborators.
     *
     * @param validationChain        the chain of validation rules; must not be {@code null}
     * @param pricingEngine          the pricing engine; must not be {@code null}
     * @param inventoryPort          the inventory reservation port; must not be {@code null}
     * @param paymentPort            the payment authorization port; must not be {@code null}
     * @param notificationDispatcher the notification dispatcher; must not be {@code null}
     * @throws IllegalArgumentException if any argument is {@code null}
     */
    protected OrderProcessingPipeline(
            ValidationChain validationChain,
            PricingEngine pricingEngine,
            InventoryPort inventoryPort,
            PaymentPort paymentPort,
            NotificationDispatcher notificationDispatcher) {

        if (validationChain == null) {
            throw new IllegalArgumentException("validationChain must not be null");
        }
        if (pricingEngine == null) {
            throw new IllegalArgumentException("pricingEngine must not be null");
        }
        if (inventoryPort == null) {
            throw new IllegalArgumentException("inventoryPort must not be null");
        }
        if (paymentPort == null) {
            throw new IllegalArgumentException("paymentPort must not be null");
        }
        if (notificationDispatcher == null) {
            throw new IllegalArgumentException("notificationDispatcher must not be null");
        }

        this.validationChain = validationChain;
        this.pricingEngine = pricingEngine;
        this.inventoryPort = inventoryPort;
        this.paymentPort = paymentPort;
        this.notificationDispatcher = notificationDispatcher;
    }

    // -------------------------------------------------------------------------
    // Template Method — fixed pipeline sequence
    // -------------------------------------------------------------------------

    /**
     * Executes the full order-processing pipeline in the fixed sequence:
     * validate → price → reserve → pay → notify.
     *
     * <p>This method is {@code final}: the stage order is an invariant of the pipeline.
     * If any stage transitions the order to {@link OrderStatus#FAILED}, the pipeline
     * short-circuits and returns the failed order immediately without executing subsequent
     * stages.
     *
     * @param order the order to process; must not be {@code null}; expected to be in
     *              {@link OrderStatus#CREATED} status
     * @return the order after all stages have been applied; never {@code null}
     * @throws IllegalArgumentException if {@code order} is {@code null}
     */
    public final Order run(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("order must not be null");
        }

        Order validated = validate(order);
        if (validated.getStatus() == OrderStatus.FAILED) {
            return validated;
        }

        Order priced = price(validated);
        if (priced.getStatus() == OrderStatus.FAILED) {
            return priced;
        }

        Order reserved = reserve(priced);
        if (reserved.getStatus() == OrderStatus.FAILED) {
            return reserved;
        }

        Order confirmed = pay(reserved);
        if (confirmed.getStatus() == OrderStatus.FAILED) {
            return confirmed;
        }

        notify(confirmed);
        return confirmed;
    }

    // -------------------------------------------------------------------------
    // Stage methods — overridable by subclasses
    // -------------------------------------------------------------------------

    /**
     * Validates the order using the {@link ValidationChain}.
     *
     * <p>On success, returns the order transitioned to {@link OrderStatus#VALIDATED}.
     * On failure, returns the order transitioned to {@link OrderStatus#FAILED} with the
     * validation rule ID and message recorded as the failure reason.
     *
     * @param order the order to validate; must not be {@code null}
     * @return the order in VALIDATED or FAILED status
     */
    protected Order validate(Order order) {
        OrderStateMachine.assertTransition(order.getStatus(), OrderStatus.VALIDATED);
        ValidationResult result = validationChain.validate(order);
        if (result.isFailed()) {
            return order.withFailure("validation_failed:" + result.getRuleId() + ":" + result.getMessage());
        }
        return order.withStatus(OrderStatus.VALIDATED);
    }

    /**
     * Prices the order using the {@link PricingEngine} with the profile returned by
     * {@link #pricingProfile()}.
     *
     * <p>On success, returns the order transitioned to {@link OrderStatus#PRICED} with all
     * five pricing totals populated. On failure (unknown profile or pricing error), returns
     * the order transitioned to {@link OrderStatus#FAILED}.
     *
     * @param order the order to price; must not be {@code null}; expected to be in VALIDATED
     *              status
     * @return the order in PRICED or FAILED status
     */
    protected Order price(Order order) {
        try {
            return pricingEngine.price(order, pricingProfile());
        } catch (Exception ex) {
            return order.withFailure("pricing_failed:" + ex.getMessage());
        }
    }

    /**
     * Reserves inventory for the order via {@link InventoryPort#reserve}.
     *
     * <p>On a successful reservation, returns the order transitioned to
     * {@link OrderStatus#RESERVED}. On out-of-stock or technical failure, returns the order
     * transitioned to {@link OrderStatus#FAILED} with an appropriate reason.
     *
     * @param order the order to reserve inventory for; must not be {@code null}; expected to
     *              be in PRICED status
     * @return the order in RESERVED or FAILED status
     */
    protected Order reserve(Order order) {
        try {
            OrderStateMachine.assertTransition(order.getStatus(), OrderStatus.RESERVED);
            ReservationResult result = inventoryPort.reserve(order.getId(), order.getItems());
            if (result.isSuccess()) {
                return order.withStatus(OrderStatus.RESERVED);
            }
            if (result instanceof ReservationResult.OutOfStock oos) {
                return order.withFailure("inventory_out_of_stock:" + oos.unavailableSkus());
            }
            return order.withFailure("inventory_reservation_failed:" + result.getReason());
        } catch (Exception ex) {
            return order.withFailure("dependency_unavailable:inventory");
        }
    }

    /**
     * Authorizes payment for the order via {@link PaymentPort#authorize}.
     *
     * <p>On a successful authorization, returns the order transitioned to
     * {@link OrderStatus#CONFIRMED}. On decline or technical failure, returns the order
     * transitioned to {@link OrderStatus#FAILED} with an appropriate reason.
     *
     * @param order the order to authorize payment for; must not be {@code null}; expected to
     *              be in RESERVED status
     * @return the order in CONFIRMED or FAILED status
     */
    protected Order pay(Order order) {
        try {
            OrderStateMachine.assertTransition(order.getStatus(), OrderStatus.CONFIRMED);
            AuthorizationResult result = paymentPort.authorize(order.getId(), order.getGrandTotal());
            if (result.isAuthorized()) {
                return order.withStatus(OrderStatus.CONFIRMED);
            }
            return order.withFailure("payment_declined:" + result.getDeclineReason());
        } catch (Exception ex) {
            return order.withFailure("dependency_unavailable:payment");
        }
    }

    /**
     * Dispatches a notification event for the confirmed order via
     * {@link NotificationDispatcher#dispatch}.
     *
     * <p>Notification failures are isolated by the dispatcher (Requirement 8.3); this method
     * does not throw even if one or more channels fail.
     *
     * @param order the confirmed order to notify about; must not be {@code null}
     */
    protected void notify(Order order) {
        OrderStatusEvent event = new OrderStatusEvent(
                order.getId(),
                OrderStatus.RESERVED,
                order.getStatus(),
                Instant.now(),
                SYSTEM_ACTOR,
                null);
        notificationDispatcher.dispatch(event);
    }

    // -------------------------------------------------------------------------
    // Abstract hook — supplied by concrete subclass
    // -------------------------------------------------------------------------

    /**
     * Returns the pricing profile name to use when pricing an order.
     *
     * <p>Concrete subclasses supply the profile (e.g., {@code "default"}, {@code "no-op"})
     * so that the pipeline can be configured without modifying this class.
     *
     * @return the pricing profile name; must not be {@code null}
     */
    protected abstract String pricingProfile();

    // -------------------------------------------------------------------------
    // Protected accessors for subclasses
    // -------------------------------------------------------------------------

    /**
     * Returns the {@link ValidationChain} used by this pipeline.
     *
     * @return the validation chain; never {@code null}
     */
    protected ValidationChain getValidationChain() {
        return validationChain;
    }

    /**
     * Returns the {@link PricingEngine} used by this pipeline.
     *
     * @return the pricing engine; never {@code null}
     */
    protected PricingEngine getPricingEngine() {
        return pricingEngine;
    }

    /**
     * Returns the {@link InventoryPort} used by this pipeline.
     *
     * @return the inventory port; never {@code null}
     */
    protected InventoryPort getInventoryPort() {
        return inventoryPort;
    }

    /**
     * Returns the {@link PaymentPort} used by this pipeline.
     *
     * @return the payment port; never {@code null}
     */
    protected PaymentPort getPaymentPort() {
        return paymentPort;
    }

    /**
     * Returns the {@link NotificationDispatcher} used by this pipeline.
     *
     * @return the notification dispatcher; never {@code null}
     */
    protected NotificationDispatcher getNotificationDispatcher() {
        return notificationDispatcher;
    }
}
