package com.orbvpn.api.domain.dto;

import com.orbvpn.api.domain.enums.NotificationChannel;
import com.orbvpn.api.domain.enums.NotificationCategory;
import lombok.Data;

import java.time.LocalTime;
import java.util.Set;

@Data
public class UpdateNotificationPreferencesInput {
    private Set<NotificationChannel> enabledChannels;
    private Set<NotificationCategory> enabledCategories;
    private boolean dndEnabled;
    private LocalTime dndStartTime;
    private LocalTime dndEndTime;
    private String timezone;
}