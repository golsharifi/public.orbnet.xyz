package com.orbvpn.api.exception;

public class InvalidNotificationException extends RuntimeException {
    public InvalidNotificationException(String message) {
        super(message);
    }
}