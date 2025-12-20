package com.orbvpn.api.exception;

public class ConcurrentSubscriptionException extends RuntimeException {
    public ConcurrentSubscriptionException(String message) {
        super(message);
    }
}
