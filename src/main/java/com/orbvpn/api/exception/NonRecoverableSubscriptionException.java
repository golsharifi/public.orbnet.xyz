package com.orbvpn.api.exception;

public class NonRecoverableSubscriptionException extends RuntimeException {
    public NonRecoverableSubscriptionException(String message) {
        super(message);
    }

    public NonRecoverableSubscriptionException(String message, Throwable cause) {
        super(message, cause);
    }
}