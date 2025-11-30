package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.entity.NotificationPreferences;
import com.orbvpn.api.domain.dto.NotificationPreferencesWithUser;
import com.orbvpn.api.domain.dto.NotificationStats;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.service.notification.MultiChannelNotificationService;
import com.orbvpn.api.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;

import org.springframework.security.access.annotation.Secured;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

@Slf4j
@Controller
@RequiredArgsConstructor
@Secured(ADMIN)
public class AdminNotificationResolver {
    private final MultiChannelNotificationService notificationService;
    private final UserService userService;

    @QueryMapping
    public NotificationPreferences getUserNotificationPreferences(@Argument String userId) {
        User user = userService.getUserById(Integer.parseInt(userId));
        return notificationService.getPreferences(user);
    }

    @QueryMapping
    public Page<NotificationPreferencesWithUser> getAllUserNotificationPreferences(
            @Argument Integer page,
            @Argument Integer size) {
        return notificationService.getAllUserPreferences(PageRequest.of(page, size));
    }

    @QueryMapping
    public NotificationStats getNotificationStats() {
        return notificationService.getNotificationStats();
    }
}