package com.orbvpn.api.exception;

public class BadCredentialsException extends RuntimeException {

    // Constructor for custom messages
    public BadCredentialsException(String message) {
        super(message);
    }
}