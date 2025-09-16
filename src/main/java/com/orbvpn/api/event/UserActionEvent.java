package com.orbvpn.api.event;

import com.orbvpn.api.domain.entity.User;
import org.springframework.context.ApplicationEvent;

public class UserActionEvent extends ApplicationEvent {
    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String PASSWORD_REENCRYPTED = "PASSWORD_REENCRYPTED";
    public static final String USER_SOFT_DELETED = "USER_SOFT_DELETED";
    public static final String USER_DELETED = "USER_DELETED";
    public static final String USER_ACCOUNT_DELETED = "USER_ACCOUNT_DELETED";

    private final User user;
    private final String action;

    public UserActionEvent(Object source, User user, String action) {
        super(source);
        this.user = user;
        this.action = action;
    }

    public User getUser() {
        return user;
    }

    public String getAction() {
        return action;
    }
}