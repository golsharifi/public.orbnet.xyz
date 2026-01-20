package com.orbvpn.api.exception;

/**
 * Exception thrown when a user attempts to connect but has already
 * reached their device/connection limit.
 */
public class DeviceLimitExceededException extends RuntimeException {
    public DeviceLimitExceededException(String message) {
        super(message);
    }

    public DeviceLimitExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
