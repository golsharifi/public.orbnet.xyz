package com.orbvpn.api.exception;

public class InvalidSubscriptionIdException extends RuntimeException {
    public InvalidSubscriptionIdException(String message) {
        super(message);
    }
}
