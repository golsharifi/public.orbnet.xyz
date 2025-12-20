package com.orbvpn.api.event;

import com.orbvpn.api.domain.entity.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class UserDeletedEvent extends ApplicationEvent {
    private final User user;

    public UserDeletedEvent(Object source, User user) {
        super(source);
        this.user = user;
    }
}
