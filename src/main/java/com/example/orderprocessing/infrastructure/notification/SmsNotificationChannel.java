package com.example.orderprocessing.infrastructure.notification;

import com.example.orderprocessing.domain.model.OrderStatusEvent;
import com.example.orderprocessing.domain.notification.OrderEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * SMS notification channel that implements {@link OrderEventListener}.
 *
 * <p>In production this would send an SMS via a gateway API (e.g., Twilio).
 * The current implementation logs the event as a stub (Requirement 8.1, 13.6).
 */
@Component
public class SmsNotificationChannel implements OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(SmsNotificationChannel.class);

    /** {@inheritDoc} */
    @Override
    public void onOrderEvent(OrderStatusEvent event) {
        log.info("[SMS] Order {} transitioned from {} to {} at {}",
                event.orderId().value(), event.from(), event.to(), event.at());
    }

    /** {@inheritDoc} */
    @Override
    public String channelName() {
        return "sms";
    }
}
