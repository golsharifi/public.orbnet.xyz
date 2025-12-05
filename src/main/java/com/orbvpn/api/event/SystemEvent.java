package com.orbvpn.api.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import java.time.LocalDateTime;

@Getter
public class SystemEvent extends ApplicationEvent {
    private final String eventType;
    private final String message;
    private final String severity;
    private final String sourceSystem;
    private final String stackTrace;
    private final String componentName;
    private final LocalDateTime eventDateTime;

    public SystemEvent(Object source, String eventType, String message, String severity,
            String componentName, String stackTrace) {
        super(source);
        this.eventType = eventType;
        this.message = message;
        this.severity = severity;
        this.sourceSystem = source.getClass().getName();
        this.stackTrace = stackTrace;
        this.componentName = componentName;
        this.eventDateTime = LocalDateTime.now();
    }

    public boolean isError() {
        return "ERROR".equalsIgnoreCase(severity);
    }

    public boolean isWarning() {
        return "WARNING".equalsIgnoreCase(severity);
    }
}
