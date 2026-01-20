// src/main/java/com/orbvpn/api/event/UserEvent.java
package com.orbvpn.api.event;

import com.orbvpn.api.domain.entity.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class UserEvent extends ApplicationEvent {
    private final User user;
    private final String eventType;
    private final String detail;

    public UserEvent(Object source, User user, String eventType) {
        this(source, user, eventType, null);
    }

    public UserEvent(Object source, User user, String eventType, String detail) {
        super(source);
        this.user = user;
        this.eventType = eventType;
        this.detail = detail;
    }
}
