package com.example.orderprocessing.infrastructure.notification;

import com.example.orderprocessing.domain.model.OrderStatusEvent;
import com.example.orderprocessing.domain.notification.OrderEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Email notification channel that implements {@link OrderEventListener}.
 *
 * <p>In production this would send an email via an SMTP client or email service API.
 * The current implementation logs the event, serving as a stub that satisfies the
 * Observer pattern wiring (Requirement 8.1, 13.6) without requiring real email
 * infrastructure.
 */
@Component
public class EmailNotificationChannel implements OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationChannel.class);

    /** {@inheritDoc} */
    @Override
    public void onOrderEvent(OrderStatusEvent event) {
        log.info("[EMAIL] Order {} transitioned from {} to {} at {}",
                event.orderId().value(), event.from(), event.to(), event.at());
    }

    /** {@inheritDoc} */
    @Override
    public String channelName() {
        return "email";
    }
}
