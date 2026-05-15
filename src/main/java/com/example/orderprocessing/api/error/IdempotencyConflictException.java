package com.example.orderprocessing.api.error;

/**
 * Thrown when an idempotency key is reused with a conflicting request payload.
 */
public class IdempotencyConflictException extends RuntimeException {

    /**
     * Constructs an {@code IdempotencyConflictException} with the given message.
     *
     * @param message a human-readable description of the conflict
     */
    public IdempotencyConflictException(String message) {
        super(message);
    }
}
