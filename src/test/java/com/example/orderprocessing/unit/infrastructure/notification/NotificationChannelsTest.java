package com.example.orderprocessing.unit.infrastructure.notification;

import com.example.orderprocessing.domain.model.OrderId;
import com.example.orderprocessing.domain.model.OrderStatus;
import com.example.orderprocessing.domain.model.OrderStatusEvent;
import com.example.orderprocessing.infrastructure.notification.EmailNotificationChannel;
import com.example.orderprocessing.infrastructure.notification.SmsNotificationChannel;
import com.example.orderprocessing.infrastructure.notification.WebhookNotificationChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for the infrastructure notification channel implementations:
 * {@link EmailNotificationChannel}, {@link SmsNotificationChannel}, and
 * {@link WebhookNotificationChannel}.
 *
 * <p>These channels are stub implementations that log events. Tests verify that:
 * <ul>
 *   <li>Each channel returns the correct stable channel name.</li>
 *   <li>Each channel's {@code onOrderEvent} does not throw for a valid event.</li>
 *   <li>Each channel handles events with null {@code from} status (initial CREATED event).</li>
 * </ul>
 *
 * <p>Validates: Requirements 8.1, 8.2, 13.6
 */
class NotificationChannelsTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private OrderStatusEvent buildEvent(OrderStatus from, OrderStatus to) {
        return new OrderStatusEvent(
                OrderId.generate(),
                from,
                to,
                Instant.now(),
                "system",
                null);
    }

    private OrderStatusEvent buildEventWithReason(OrderStatus from, OrderStatus to, String reason) {
        return new OrderStatusEvent(
                OrderId.generate(),
                from,
                to,
                Instant.now(),
                "system",
                reason);
    }

    // =========================================================================
    // EmailNotificationChannel
    // =========================================================================

    @Test
    @DisplayName("Email: channelName returns 'email'")
    void email_channelName_returnsEmail() {
        EmailNotificationChannel channel = new EmailNotificationChannel();
        assertThat(channel.channelName()).isEqualTo("email");
    }

    @Test
    @DisplayName("Email: onOrderEvent does not throw for a standard transition event")
    void email_onOrderEvent_doesNotThrow() {
        EmailNotificationChannel channel = new EmailNotificationChannel();
        OrderStatusEvent event = buildEvent(OrderStatus.RESERVED, OrderStatus.CONFIRMED);

        assertThatCode(() -> channel.onOrderEvent(event)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Email: onOrderEvent does not throw for initial CREATED event (null from)")
    void email_onOrderEvent_nullFromStatus_doesNotThrow() {
        EmailNotificationChannel channel = new EmailNotificationChannel();
        OrderStatusEvent event = buildEvent(null, OrderStatus.CREATED);

        assertThatCode(() -> channel.onOrderEvent(event)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Email: onOrderEvent does not throw for FAILED event with reason")
    void email_onOrderEvent_failedEventWithReason_doesNotThrow() {
        EmailNotificationChannel channel = new EmailNotificationChannel();
        OrderStatusEvent event = buildEventWithReason(
                OrderStatus.CREATED, OrderStatus.FAILED, "validation_failed:NON_EMPTY_ITEMS");

        assertThatCode(() -> channel.onOrderEvent(event)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Email: onOrderEvent does not throw for CANCELLED event")
    void email_onOrderEvent_cancelledEvent_doesNotThrow() {
        EmailNotificationChannel channel = new EmailNotificationChannel();
        OrderStatusEvent event = buildEventWithReason(
                OrderStatus.CONFIRMED, OrderStatus.CANCELLED, "customer request");

        assertThatCode(() -> channel.onOrderEvent(event)).doesNotThrowAnyException();
    }

    // =========================================================================
    // SmsNotificationChannel
    // =========================================================================

    @Test
    @DisplayName("SMS: channelName returns 'sms'")
    void sms_channelName_returnsSms() {
        SmsNotificationChannel channel = new SmsNotificationChannel();
        assertThat(channel.channelName()).isEqualTo("sms");
    }

    @Test
    @DisplayName("SMS: onOrderEvent does not throw for a standard transition event")
    void sms_onOrderEvent_doesNotThrow() {
        SmsNotificationChannel channel = new SmsNotificationChannel();
        OrderStatusEvent event = buildEvent(OrderStatus.RESERVED, OrderStatus.CONFIRMED);

        assertThatCode(() -> channel.onOrderEvent(event)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SMS: onOrderEvent does not throw for initial CREATED event (null from)")
    void sms_onOrderEvent_nullFromStatus_doesNotThrow() {
        SmsNotificationChannel channel = new SmsNotificationChannel();
        OrderStatusEvent event = buildEvent(null, OrderStatus.CREATED);

        assertThatCode(() -> channel.onOrderEvent(event)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SMS: onOrderEvent does not throw for SHIPPED event")
    void sms_onOrderEvent_shippedEvent_doesNotThrow() {
        SmsNotificationChannel channel = new SmsNotificationChannel();
        OrderStatusEvent event = buildEvent(OrderStatus.CONFIRMED, OrderStatus.SHIPPED);

        assertThatCode(() -> channel.onOrderEvent(event)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SMS: onOrderEvent does not throw for DELIVERED event")
    void sms_onOrderEvent_deliveredEvent_doesNotThrow() {
        SmsNotificationChannel channel = new SmsNotificationChannel();
        OrderStatusEvent event = buildEvent(OrderStatus.SHIPPED, OrderStatus.DELIVERED);

        assertThatCode(() -> channel.onOrderEvent(event)).doesNotThrowAnyException();
    }

    // =========================================================================
    // WebhookNotificationChannel
    // =========================================================================

    @Test
    @DisplayName("Webhook: channelName returns 'webhook'")
    void webhook_channelName_returnsWebhook() {
        WebhookNotificationChannel channel = new WebhookNotificationChannel();
        assertThat(channel.channelName()).isEqualTo("webhook");
    }

    @Test
    @DisplayName("Webhook: onOrderEvent does not throw for a standard transition event")
    void webhook_onOrderEvent_doesNotThrow() {
        WebhookNotificationChannel channel = new WebhookNotificationChannel();
        OrderStatusEvent event = buildEvent(OrderStatus.RESERVED, OrderStatus.CONFIRMED);

        assertThatCode(() -> channel.onOrderEvent(event)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Webhook: onOrderEvent does not throw for initial CREATED event (null from)")
    void webhook_onOrderEvent_nullFromStatus_doesNotThrow() {
        WebhookNotificationChannel channel = new WebhookNotificationChannel();
        OrderStatusEvent event = buildEvent(null, OrderStatus.CREATED);

        assertThatCode(() -> channel.onOrderEvent(event)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Webhook: onOrderEvent does not throw for FAILED event with reason")
    void webhook_onOrderEvent_failedEventWithReason_doesNotThrow() {
        WebhookNotificationChannel channel = new WebhookNotificationChannel();
        OrderStatusEvent event = buildEventWithReason(
                OrderStatus.RESERVED, OrderStatus.FAILED, "dependency_unavailable:payment");

        assertThatCode(() -> channel.onOrderEvent(event)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Webhook: onOrderEvent does not throw for VALIDATED event")
    void webhook_onOrderEvent_validatedEvent_doesNotThrow() {
        WebhookNotificationChannel channel = new WebhookNotificationChannel();
        OrderStatusEvent event = buildEvent(OrderStatus.CREATED, OrderStatus.VALIDATED);

        assertThatCode(() -> channel.onOrderEvent(event)).doesNotThrowAnyException();
    }

    // =========================================================================
    // Channel name uniqueness
    // =========================================================================

    @Test
    @DisplayName("All three channels have distinct channel names")
    void allChannels_haveDistinctNames() {
        String emailName = new EmailNotificationChannel().channelName();
        String smsName = new SmsNotificationChannel().channelName();
        String webhookName = new WebhookNotificationChannel().channelName();

        assertThat(emailName).isNotEqualTo(smsName);
        assertThat(emailName).isNotEqualTo(webhookName);
        assertThat(smsName).isNotEqualTo(webhookName);
    }

    // =========================================================================
    // Integration with NotificationDispatcher
    // =========================================================================

    @Test
    @DisplayName("All three channels can be registered with NotificationDispatcher without error")
    void allChannels_canBeRegisteredWithDispatcher() {
        EmailNotificationChannel email = new EmailNotificationChannel();
        SmsNotificationChannel sms = new SmsNotificationChannel();
        WebhookNotificationChannel webhook = new WebhookNotificationChannel();

        com.example.orderprocessing.domain.notification.NotificationDispatcher dispatcher =
                new com.example.orderprocessing.domain.notification.NotificationDispatcher(
                        java.util.List.of(email, sms, webhook));

        assertThat(dispatcher.getRegisteredChannels())
                .containsExactly("email", "sms", "webhook");
    }

    @Test
    @DisplayName("All three channels receive the event when dispatched via NotificationDispatcher")
    void allChannels_receiveEventViaDispatcher() {
        EmailNotificationChannel email = new EmailNotificationChannel();
        SmsNotificationChannel sms = new SmsNotificationChannel();
        WebhookNotificationChannel webhook = new WebhookNotificationChannel();

        com.example.orderprocessing.domain.notification.NotificationDispatcher dispatcher =
                new com.example.orderprocessing.domain.notification.NotificationDispatcher(
                        java.util.List.of(email, sms, webhook));

        OrderStatusEvent event = buildEvent(OrderStatus.RESERVED, OrderStatus.CONFIRMED);

        // Should not throw — all channels are stub implementations that only log
        assertThatCode(() -> dispatcher.dispatch(event)).doesNotThrowAnyException();
        assertThat(dispatcher.getFailedChannels()).isEmpty();
    }
}
