package com.example.orderprocessing.api.error;

/**
 * Thrown when a requested order cannot be found in the repository.
 */
public class OrderNotFoundException extends RuntimeException {

    /**
     * Constructs an {@code OrderNotFoundException} with the given message.
     *
     * @param message a human-readable description identifying the missing order
     */
    public OrderNotFoundException(String message) {
        super(message);
    }
}
