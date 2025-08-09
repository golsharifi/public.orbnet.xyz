package com.orbvpn.api.exception;

public class RecoverableSubscriptionException extends RuntimeException {
    public RecoverableSubscriptionException(String message) {
        super(message);
    }

    public RecoverableSubscriptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
