package com.orbvpn.api.exception;

public class DuplicatePurchaseTokenException extends RuntimeException {
    public DuplicatePurchaseTokenException(String message) {
        super(message);
    }
}