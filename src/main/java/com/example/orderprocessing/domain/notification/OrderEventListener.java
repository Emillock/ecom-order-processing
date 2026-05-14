package com.example.orderprocessing.domain.notification;

import com.example.orderprocessing.domain.model.OrderStatusEvent;

/**
 * Observer interface for order lifecycle events.
 *
 * <p>Implementations represent individual notification channels (e.g., email, SMS, webhook).
 * Each channel registers itself as a listener with the {@link NotificationDispatcher}; the
 * dispatcher fans out every {@link OrderStatusEvent} to all registered listeners without
 * the dispatcher needing to know about any specific channel (OCP — Requirement 13.6).
 *
 * <p>No Spring imports — this is a pure domain interface (Requirement 14.3).
 *
 * @see NotificationDispatcher
 * @see OrderStatusEvent
 */
public interface OrderEventListener {

    /**
     * Called by the {@link NotificationDispatcher} when an order status transition has been
     * accepted and persisted.
     *
     * <p>Implementations must not assume any particular invocation order relative to other
     * registered listeners. If this method throws, the dispatcher will record the failure and
     * continue notifying the remaining listeners (Requirement 8.3).
     *
     * @param event the event payload describing the transition; never {@code null}
     */
    void onOrderEvent(OrderStatusEvent event);

    /**
     * Returns the stable identifier for this notification channel.
     *
     * <p>The channel name is used for logging and diagnostics, and is also used by
     * {@link NotificationDispatcher#getFailedChannels()} to report which channels failed
     * during the last dispatch. Examples: {@code "email"}, {@code "sms"}, {@code "webhook"}.
     *
     * @return a non-null, non-blank channel identifier
     */
    String channelName();
}
