package com.orbvpn.api.domain.dto;

import com.orbvpn.api.domain.entity.NotificationPreferences;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreferencesWithUser {
    private Integer userId;
    private String userEmail;
    private NotificationPreferences preferences;
}
