package com.orbvpn.api.exception;

public class InvalidPurchaseTokenException extends RuntimeException {
    public InvalidPurchaseTokenException(String message) {
        super(message);
    }
}
