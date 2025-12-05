package com.orbvpn.api.domain.dto;

import com.orbvpn.api.domain.enums.NotificationChannel;
import com.orbvpn.api.domain.enums.NotificationCategory;
import lombok.Data;
import java.util.List;

@Data
public class NotificationStats {
    private int totalNotificationsSent;
    private List<ChannelStat> notificationsByChannel;
    private List<CategoryStat> notificationsByCategory;
    private int failedNotifications;

    @Data
    public static class ChannelStat {
        private NotificationChannel channel;
        private int count;
        private float successRate;
    }

    @Data
    public static class CategoryStat {
        private NotificationCategory category;
        private int count;
    }
}