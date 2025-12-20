package com.orbvpn.api.exception;

import lombok.Getter;

@Getter
public class NotificationDeliveryException extends RuntimeException {
    private final String channel;
    private final String recipient;
    private final String errorCode;
    private final Integer statusCode;

    public NotificationDeliveryException(String message) {
        super(message);
        this.channel = null;
        this.recipient = null;
        this.errorCode = null;
        this.statusCode = null;
    }

    public NotificationDeliveryException(String message, Throwable cause) {
        super(message, cause);
        this.channel = null;
        this.recipient = null;
        this.errorCode = null;
        this.statusCode = null;
    }

    public NotificationDeliveryException(String message, String channel, String recipient) {
        super(message);
        this.channel = channel;
        this.recipient = recipient;
        this.errorCode = null;
        this.statusCode = null;
    }

    public NotificationDeliveryException(String message, String channel, String recipient, String errorCode) {
        super(message);
        this.channel = channel;
        this.recipient = recipient;
        this.errorCode = errorCode;
        this.statusCode = null;
    }

    public NotificationDeliveryException(String message, String channel, String recipient,
            String errorCode, Throwable cause) {
        super(message, cause);
        this.channel = channel;
        this.recipient = recipient;
        this.errorCode = errorCode;
        this.statusCode = null;
    }

    public NotificationDeliveryException(String message, String channel, String recipient,
            Integer statusCode, Throwable cause) {
        super(message, cause);
        this.channel = channel;
        this.recipient = recipient;
        this.errorCode = String.valueOf(statusCode);
        this.statusCode = statusCode;
    }

    public NotificationDeliveryException(String message, String channel, String recipient,
            String errorCode, Integer statusCode) {
        super(message);
        this.channel = channel;
        this.recipient = recipient;
        this.errorCode = errorCode;
        this.statusCode = statusCode;
    }

    public NotificationDeliveryException(String message, String channel, String recipient,
            String errorCode, Integer statusCode, Throwable cause) {
        super(message, cause);
        this.channel = channel;
        this.recipient = recipient;
        this.errorCode = errorCode;
        this.statusCode = statusCode;
    }

    @Override
    public String toString() {
        return String.format(
                "NotificationDeliveryException[channel=%s, recipient=%s, errorCode=%s, statusCode=%d, message=%s]",
                channel, recipient, errorCode, statusCode, getMessage());
    }
}
