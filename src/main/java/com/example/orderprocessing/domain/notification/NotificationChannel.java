package com.example.orderprocessing.domain.notification;

/**
 * Enumerates the supported notification delivery channels for order status events.
 *
 * <p>Each channel represents a distinct delivery mechanism. New channels can be added
 * here and registered with the {@code NotificationDispatcher} without modifying
 * existing channel implementations (Open/Closed Principle, Requirement 8.2).
 *
 * <p>This enum is pure domain — it carries no Spring or infrastructure imports.
 */
public enum NotificationChannel {

    /** Delivers notifications via electronic mail. */
    EMAIL,

    /** Delivers notifications via Short Message Service. */
    SMS,

    /** Delivers notifications via an HTTP webhook callback. */
    WEBHOOK;

    /**
     * Returns the lowercase string name of this channel (e.g., {@code "email"}, {@code "sms"},
     * {@code "webhook"}).
     *
     * @return the lowercase channel name
     */
    public String channelName() {
        return name().toLowerCase();
    }
}
