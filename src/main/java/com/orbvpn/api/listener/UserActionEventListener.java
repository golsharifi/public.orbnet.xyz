package com.orbvpn.api.listener;

import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.event.UserActionEvent;
import com.orbvpn.api.service.UserDeviceService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class UserActionEventListener {
    private final UserDeviceService userDeviceService;

    public UserActionEventListener(UserDeviceService userDeviceService) {
        this.userDeviceService = userDeviceService;
    }

    @EventListener
    public void handleUserActionEvent(UserActionEvent event) {
        User user = event.getUser();
        String action = event.getAction();

        switch (action) {
            case "PASSWORD_CHANGED":
            case "PASSWORD_RESET":
            case "PASSWORD_REENCRYPTED":
            case "USER_DELETED":
            case "USER_ACCOUNT_DELETED":
                userDeviceService.logoutAllDevices(user);
                break;
            default:
                break;
        }
    }
}