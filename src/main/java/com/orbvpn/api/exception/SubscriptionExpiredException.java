package com.orbvpn.api.exception;

/**
 * Exception thrown when a user attempts to connect with an expired
 * or invalid subscription.
 */
public class SubscriptionExpiredException extends RuntimeException {
    public SubscriptionExpiredException(String message) {
        super(message);
    }

    public SubscriptionExpiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
