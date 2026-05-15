package com.example.orderprocessing.unit.domain.notification;

import com.example.orderprocessing.domain.model.OrderId;
import com.example.orderprocessing.domain.model.OrderStatus;
import com.example.orderprocessing.domain.model.OrderStatusEvent;
import com.example.orderprocessing.domain.notification.NotificationDispatcher;
import com.example.orderprocessing.domain.notification.OrderEventListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NotificationDispatcher}.
 *
 * <p>Covers: fan-out to all listeners, failure isolation (one listener throws, others
 * still receive the event), failed-channel recording, and null-argument guards.
 *
 * <p>Validates: Requirements 8.2, 8.3
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationDispatcherTest {

    @Mock
    private OrderEventListener listenerA;

    @Mock
    private OrderEventListener listenerB;

    @Mock
    private OrderEventListener listenerC;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private OrderStatusEvent buildEvent() {
        return new OrderStatusEvent(
                OrderId.generate(),
                OrderStatus.RESERVED,
                OrderStatus.CONFIRMED,
                Instant.now(),
                "system",
                null);
    }

    // =========================================================================
    // Constructor guards
    // =========================================================================

    @Test
    @DisplayName("Constructor: throws IllegalArgumentException when listeners list is null")
    void constructor_nullListeners_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new NotificationDispatcher(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("listeners");
    }

    @Test
    @DisplayName("Constructor: accepts empty listeners list without throwing")
    void constructor_emptyListeners_doesNotThrow() {
        NotificationDispatcher dispatcher = new NotificationDispatcher(List.of());
        assertThat(dispatcher.getRegisteredChannels()).isEmpty();
    }

    // =========================================================================
    // dispatch() — null guard
    // =========================================================================

    @Test
    @DisplayName("dispatch: throws IllegalArgumentException when event is null")
    void dispatch_nullEvent_throwsIllegalArgumentException() {
        NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(listenerA));

        assertThatThrownBy(() -> dispatcher.dispatch(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("event");
    }

    // =========================================================================
    // Fan-out: all registered listeners receive the event
    // =========================================================================

    @Test
    @DisplayName("Fan-out: single listener receives the dispatched event")
    void dispatch_singleListener_receivesEvent() {
        when(listenerA.channelName()).thenReturn("email");
        NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(listenerA));
        OrderStatusEvent event = buildEvent();

        dispatcher.dispatch(event);

        verify(listenerA).onOrderEvent(event);
    }

    @Test
    @DisplayName("Fan-out: all three registered listeners receive the same event")
    void dispatch_multipleListeners_allReceiveEvent() {
        when(listenerA.channelName()).thenReturn("email");
        when(listenerB.channelName()).thenReturn("sms");
        when(listenerC.channelName()).thenReturn("webhook");
        NotificationDispatcher dispatcher = new NotificationDispatcher(
                List.of(listenerA, listenerB, listenerC));
        OrderStatusEvent event = buildEvent();

        dispatcher.dispatch(event);

        verify(listenerA).onOrderEvent(event);
        verify(listenerB).onOrderEvent(event);
        verify(listenerC).onOrderEvent(event);
    }

    @Test
    @DisplayName("Fan-out: no listeners registered means no calls and no failures")
    void dispatch_noListeners_noFailures() {
        NotificationDispatcher dispatcher = new NotificationDispatcher(List.of());
        OrderStatusEvent event = buildEvent();

        dispatcher.dispatch(event);

        assertThat(dispatcher.getFailedChannels()).isEmpty();
    }

    // =========================================================================
    // Failure isolation: one listener throws, remaining listeners still receive
    // =========================================================================

    @Test
    @DisplayName("Isolation: when first listener throws, second and third still receive the event")
    void dispatch_firstListenerThrows_remainingListenersStillReceiveEvent() {
        when(listenerA.channelName()).thenReturn("email");
        when(listenerB.channelName()).thenReturn("sms");
        when(listenerC.channelName()).thenReturn("webhook");
        doThrow(new RuntimeException("email service down")).when(listenerA).onOrderEvent(org.mockito.ArgumentMatchers.any());

        NotificationDispatcher dispatcher = new NotificationDispatcher(
                List.of(listenerA, listenerB, listenerC));
        OrderStatusEvent event = buildEvent();

        // Should not throw — dispatcher isolates failures
        dispatcher.dispatch(event);

        verify(listenerB).onOrderEvent(event);
        verify(listenerC).onOrderEvent(event);
    }

    @Test
    @DisplayName("Isolation: when middle listener throws, first and third still receive the event")
    void dispatch_middleListenerThrows_firstAndThirdStillReceiveEvent() {
        when(listenerA.channelName()).thenReturn("email");
        when(listenerB.channelName()).thenReturn("sms");
        when(listenerC.channelName()).thenReturn("webhook");
        doThrow(new RuntimeException("sms gateway timeout")).when(listenerB).onOrderEvent(org.mockito.ArgumentMatchers.any());

        NotificationDispatcher dispatcher = new NotificationDispatcher(
                List.of(listenerA, listenerB, listenerC));
        OrderStatusEvent event = buildEvent();

        dispatcher.dispatch(event);

        verify(listenerA).onOrderEvent(event);
        verify(listenerC).onOrderEvent(event);
    }

    @Test
    @DisplayName("Isolation: when last listener throws, first and second still receive the event")
    void dispatch_lastListenerThrows_firstAndSecondStillReceiveEvent() {
        when(listenerA.channelName()).thenReturn("email");
        when(listenerB.channelName()).thenReturn("sms");
        when(listenerC.channelName()).thenReturn("webhook");
        doThrow(new RuntimeException("webhook unreachable")).when(listenerC).onOrderEvent(org.mockito.ArgumentMatchers.any());

        NotificationDispatcher dispatcher = new NotificationDispatcher(
                List.of(listenerA, listenerB, listenerC));
        OrderStatusEvent event = buildEvent();

        dispatcher.dispatch(event);

        verify(listenerA).onOrderEvent(event);
        verify(listenerB).onOrderEvent(event);
    }

    @Test
    @DisplayName("Isolation: when all listeners throw, dispatch does not propagate any exception")
    void dispatch_allListenersThrow_doesNotPropagateException() {
        when(listenerA.channelName()).thenReturn("email");
        when(listenerB.channelName()).thenReturn("sms");
        doThrow(new RuntimeException("email down")).when(listenerA).onOrderEvent(org.mockito.ArgumentMatchers.any());
        doThrow(new RuntimeException("sms down")).when(listenerB).onOrderEvent(org.mockito.ArgumentMatchers.any());

        NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(listenerA, listenerB));
        OrderStatusEvent event = buildEvent();

        // Must not throw
        dispatcher.dispatch(event);
    }

    // =========================================================================
    // Failed-channel recording
    // =========================================================================

    @Test
    @DisplayName("Failed channels: empty before any dispatch")
    void getFailedChannels_beforeDispatch_isEmpty() {
        when(listenerA.channelName()).thenReturn("email");
        NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(listenerA));

        assertThat(dispatcher.getFailedChannels()).isEmpty();
    }

    @Test
    @DisplayName("Failed channels: records the channel name of the throwing listener")
    void dispatch_listenerThrows_failedChannelRecorded() {
        when(listenerA.channelName()).thenReturn("email");
        doThrow(new RuntimeException("email service down")).when(listenerA).onOrderEvent(org.mockito.ArgumentMatchers.any());

        NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(listenerA));
        OrderStatusEvent event = buildEvent();

        dispatcher.dispatch(event);

        assertThat(dispatcher.getFailedChannels()).containsExactly("email");
    }

    @Test
    @DisplayName("Failed channels: records all throwing channels when multiple fail")
    void dispatch_multipleListenersThrow_allFailedChannelsRecorded() {
        when(listenerA.channelName()).thenReturn("email");
        when(listenerB.channelName()).thenReturn("sms");
        when(listenerC.channelName()).thenReturn("webhook");
        doThrow(new RuntimeException("email down")).when(listenerA).onOrderEvent(org.mockito.ArgumentMatchers.any());
        doThrow(new RuntimeException("sms down")).when(listenerB).onOrderEvent(org.mockito.ArgumentMatchers.any());

        NotificationDispatcher dispatcher = new NotificationDispatcher(
                List.of(listenerA, listenerB, listenerC));
        OrderStatusEvent event = buildEvent();

        dispatcher.dispatch(event);

        assertThat(dispatcher.getFailedChannels()).containsExactlyInAnyOrder("email", "sms");
    }

    @Test
    @DisplayName("Failed channels: empty when all listeners succeed")
    void dispatch_allListenersSucceed_failedChannelsEmpty() {
        when(listenerA.channelName()).thenReturn("email");
        when(listenerB.channelName()).thenReturn("sms");

        NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(listenerA, listenerB));
        OrderStatusEvent event = buildEvent();

        dispatcher.dispatch(event);

        assertThat(dispatcher.getFailedChannels()).isEmpty();
    }

    @Test
    @DisplayName("Failed channels: reset on each dispatch call (reflects only most recent invocation)")
    void dispatch_secondDispatchSucceeds_failedChannelsClearedFromPreviousRun() {
        when(listenerA.channelName()).thenReturn("email");
        doThrow(new RuntimeException("transient failure"))
                .doNothing()
                .when(listenerA).onOrderEvent(org.mockito.ArgumentMatchers.any());

        NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(listenerA));
        OrderStatusEvent event = buildEvent();

        // First dispatch — listener throws
        dispatcher.dispatch(event);
        assertThat(dispatcher.getFailedChannels()).containsExactly("email");

        // Second dispatch — listener succeeds; failed channels must be cleared
        dispatcher.dispatch(event);
        assertThat(dispatcher.getFailedChannels()).isEmpty();
    }

    // =========================================================================
    // getRegisteredChannels()
    // =========================================================================

    @Test
    @DisplayName("getRegisteredChannels: returns channel names of all registered listeners")
    void getRegisteredChannels_returnsAllChannelNames() {
        when(listenerA.channelName()).thenReturn("email");
        when(listenerB.channelName()).thenReturn("sms");
        when(listenerC.channelName()).thenReturn("webhook");

        NotificationDispatcher dispatcher = new NotificationDispatcher(
                List.of(listenerA, listenerB, listenerC));

        assertThat(dispatcher.getRegisteredChannels())
                .containsExactly("email", "sms", "webhook");
    }

    @Test
    @DisplayName("getRegisteredChannels: returns empty list when no listeners registered")
    void getRegisteredChannels_noListeners_returnsEmptyList() {
        NotificationDispatcher dispatcher = new NotificationDispatcher(List.of());

        assertThat(dispatcher.getRegisteredChannels()).isEmpty();
    }

    // =========================================================================
    // Listener that throws a checked-exception-wrapping RuntimeException
    // =========================================================================

    @Test
    @DisplayName("Isolation: listener throwing Error subclass is still caught and isolated")
    void dispatch_listenerThrowsError_isolatedAndChannelRecorded() {
        when(listenerA.channelName()).thenReturn("email");
        when(listenerB.channelName()).thenReturn("sms");
        // Throw a RuntimeException (not Error — dispatcher catches Exception)
        doThrow(new RuntimeException("unexpected error")).when(listenerA).onOrderEvent(org.mockito.ArgumentMatchers.any());

        NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(listenerA, listenerB));
        OrderStatusEvent event = buildEvent();

        dispatcher.dispatch(event);

        verify(listenerB).onOrderEvent(event);
        assertThat(dispatcher.getFailedChannels()).containsExactly("email");
    }

    // =========================================================================
    // Listener that is never called when no event is dispatched
    // =========================================================================

    @Test
    @DisplayName("No dispatch: listener is never called when dispatch is not invoked")
    void noDispatch_listenerNeverCalled() {
        NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(listenerA));

        verify(listenerA, never()).onOrderEvent(org.mockito.ArgumentMatchers.any());
    }
}
