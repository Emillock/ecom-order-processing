package com.example.orderprocessing.api.error;

/**
 * Thrown when a requested order lifecycle transition is not permitted by the state machine.
 */
public class InvalidTransitionException extends RuntimeException {

    /**
     * Constructs an {@code InvalidTransitionException} with the given message.
     *
     * @param message a human-readable description of the invalid transition
     */
    public InvalidTransitionException(String message) {
        super(message);
    }

    /**
     * Constructs an {@code InvalidTransitionException} wrapping a cause.
     *
     * @param message a human-readable description of the invalid transition
     * @param cause   the underlying exception that triggered this error
     */
    public InvalidTransitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
