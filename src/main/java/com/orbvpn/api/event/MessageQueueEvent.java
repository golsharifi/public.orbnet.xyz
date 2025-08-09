package com.orbvpn.api.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class MessageQueueEvent extends ApplicationEvent {
    private final String recipient;
    private final String message;
    private final MessageType type;

    public enum MessageType {
        WHATSAPP,
        TELEGRAM
    }

    public MessageQueueEvent(Object source, String recipient, String message, MessageType type) {
        super(source);
        this.recipient = recipient;
        this.message = message;
        this.type = type;
    }
}