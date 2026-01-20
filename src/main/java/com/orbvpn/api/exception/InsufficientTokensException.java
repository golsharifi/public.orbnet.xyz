package com.orbvpn.api.exception;

public class InsufficientTokensException extends RuntimeException {
    public InsufficientTokensException(String message) {
        super(message);
    }

    public InsufficientTokensException(String message, Throwable cause) {
        super(message, cause);
    }
}