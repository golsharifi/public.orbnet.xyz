package com.orbvpn.api.exception;

/**
 * Exception thrown when a user attempts to connect but has exceeded
 * their bandwidth quota.
 */
public class BandwidthExceededException extends RuntimeException {
    public BandwidthExceededException(String message) {
        super(message);
    }

    public BandwidthExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
