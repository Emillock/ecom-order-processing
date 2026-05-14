package com.example.orderprocessing.domain.notification;

import com.example.orderprocessing.domain.model.OrderStatusEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Subject in the Observer pattern — fans out {@link OrderStatusEvent} instances to all
 * registered {@link OrderEventListener} implementations.
 *
 * <p>New notification channels are added by registering a new {@link OrderEventListener} bean
 * in the application context and passing it to this dispatcher's constructor. The dispatcher
 * itself never needs to be modified (OCP — Requirement 13.6).
 *
 * <p>Failure isolation (Requirement 8.3): if a listener throws any {@link Exception}, the
 * dispatcher logs the failure at WARNING level, records the channel name in the failed-channels
 * list, and continues delivering the event to the remaining listeners. A single misbehaving
 * channel cannot prevent other channels from receiving the notification.
 *
 * <p>Thread safety: the listener list is stored in a {@link CopyOnWriteArrayList} so that
 * concurrent reads during dispatch are safe without locking. The failed-channels list is
 * replaced atomically after each dispatch call.
 *
 * <p>No Spring imports — this is a pure domain class (Requirement 14.3). Spring wiring
 * (constructor injection of the listener list) is handled by the infrastructure configuration
 * layer.
 */
public final class NotificationDispatcher {

    private static final Logger LOGGER = Logger.getLogger(NotificationDispatcher.class.getName());

    /** Thread-safe snapshot list of registered listeners. */
    private final CopyOnWriteArrayList<OrderEventListener> listeners;

    /**
     * Channels that threw an exception during the most recent {@link #dispatch} call.
     * Replaced atomically after every dispatch; never {@code null}.
     */
    private volatile List<String> failedChannels = Collections.emptyList();

    /**
     * Creates a dispatcher pre-loaded with the given listeners.
     *
     * @param listeners the ordered list of {@link OrderEventListener} instances to notify;
     *                  must not be {@code null} (may be empty)
     * @throws IllegalArgumentException if {@code listeners} is {@code null}
     */
    public NotificationDispatcher(List<OrderEventListener> listeners) {
        if (listeners == null) {
            throw new IllegalArgumentException("listeners must not be null");
        }
        this.listeners = new CopyOnWriteArrayList<>(listeners);
    }

    /**
     * Dispatches the given event to every registered {@link OrderEventListener}.
     *
     * <p>Listeners are invoked in registration order. If a listener throws, the exception is
     * caught, logged at WARNING level (including the channel name and order ID for
     * diagnostics), the channel name is recorded in the failed-channels list, and dispatch
     * continues to the next listener (Requirement 8.3).
     *
     * <p>The failed-channels list is reset at the start of each dispatch call, so
     * {@link #getFailedChannels()} always reflects only the most recent invocation.
     *
     * @param event the event to dispatch; must not be {@code null}
     * @throws IllegalArgumentException if {@code event} is {@code null}
     */
    public void dispatch(OrderStatusEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }

        List<String> failures = new ArrayList<>();

        for (OrderEventListener listener : listeners) {
            try {
                listener.onOrderEvent(event);
            } catch (Exception ex) {
                failures.add(listener.channelName());
                LOGGER.log(
                        Level.WARNING,
                        "Notification channel ''{0}'' failed for order {1} transitioning to {2}: {3}",
                        new Object[]{
                                listener.channelName(),
                                event.orderId().value(),
                                event.to(),
                                ex.getMessage()
                        }
                );
            }
        }

        this.failedChannels = Collections.unmodifiableList(failures);
    }

    /**
     * Returns the channel names of all currently registered {@link OrderEventListener}
     * instances.
     *
     * <p>The returned list reflects the listeners supplied at construction time. It can be
     * used for diagnostics, health checks, or to verify that expected channels are wired in.
     *
     * @return an unmodifiable, possibly empty list of registered channel names; never {@code null}
     */
    public List<String> getRegisteredChannels() {
        return listeners.stream()
                .map(OrderEventListener::channelName)
                .toList();
    }

    /**
     * Returns the names of channels that threw an exception during the most recent
     * {@link #dispatch} call.
     *
     * <p>The list is reset at the beginning of every {@code dispatch} invocation, so callers
     * should read it immediately after dispatching if they need to act on failures. Returns an
     * empty list if no dispatch has been performed yet or if the last dispatch succeeded for
     * all channels.
     *
     * @return an unmodifiable, possibly empty list of failed channel names; never {@code null}
     */
    public List<String> getFailedChannels() {
        return failedChannels;
    }
}
