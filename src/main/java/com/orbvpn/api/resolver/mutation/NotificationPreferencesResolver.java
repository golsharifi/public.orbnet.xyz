package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.UpdateNotificationPreferencesInput;
import com.orbvpn.api.domain.entity.NotificationPreferences;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.service.UserService;
import com.orbvpn.api.service.notification.MultiChannelNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class NotificationPreferencesResolver {
    private final MultiChannelNotificationService notificationService;
    private final UserService userService;

    @QueryMapping
    public NotificationPreferences getNotificationPreferences() {
        User user = userService.getUser();
        return notificationService.getPreferences(user);
    }

    @MutationMapping
    public NotificationPreferences updateNotificationPreferences(
            @Argument UpdateNotificationPreferencesInput input) {
        User user = userService.getUser();

        return notificationService.updateNotificationPreferences(
                user,
                input.getEnabledChannels(),
                input.getEnabledCategories(),
                input.isDndEnabled(),
                input.getDndStartTime(),
                input.getDndEndTime(),
                input.getTimezone() != null ? input.getTimezone() : "UTC");
    }
}