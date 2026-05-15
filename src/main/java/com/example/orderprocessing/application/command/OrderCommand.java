package com.example.orderprocessing.application.command;

import com.example.orderprocessing.domain.model.Order;

/**
 * Command interface for all order lifecycle operations (Command pattern, Requirement 13.3).
 *
 * <p>Each concrete command encapsulates a single state-mutating action on an {@link Order},
 * runs it under the per-{@code OrderId} lock provided by
 * {@link com.example.orderprocessing.application.concurrency.OrderLockRegistry}, and records
 * an {@link com.example.orderprocessing.domain.model.OrderStatusEvent} for the audit log
 * (Requirements 6.3, 7.1, 11.3).
 *
 * <p>Implementations live in the {@code application.command} package and depend only on
 * domain ports and model types — no infrastructure imports (Requirement 14.3).
 */
public interface OrderCommand {

    /**
     * Executes the command and returns the resulting {@link Order}.
     *
     * <p>Implementations must:
     * <ul>
     *   <li>Acquire the per-{@code OrderId} lock before mutating state.</li>
     *   <li>Validate the requested transition via
     *       {@link com.example.orderprocessing.domain.lifecycle.OrderStateMachine#assertTransition}.</li>
     *   <li>Persist the updated order and append an
     *       {@link com.example.orderprocessing.domain.model.OrderStatusEvent} to the audit log.</li>
     * </ul>
     *
     * @return the updated {@link Order} after the command has been applied; never {@code null}
     * @throws IllegalStateException if the requested transition is not permitted by the
     *                               lifecycle graph
     */
    Order execute();
}
