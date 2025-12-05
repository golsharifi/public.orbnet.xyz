package com.orbvpn.api.service.notification;

import com.orbvpn.api.domain.dto.NotificationDto;
import com.orbvpn.api.domain.dto.NotificationPreferencesWithUser;
import com.orbvpn.api.domain.entity.NotificationPreferences;
import com.orbvpn.api.domain.dto.NotificationStats;
import com.orbvpn.api.repository.NotificationStatsRepository;
import com.orbvpn.api.domain.enums.NotificationCategory;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserProfile;
import com.orbvpn.api.domain.enums.NotificationChannel;
import com.orbvpn.api.repository.NotificationPreferencesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.time.LocalTime;
import java.time.LocalDate;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class MultiChannelNotificationService {
    private final TelegramService telegramService;
    private final WhatsAppService whatsAppService;
    private final SmsService smsService;
    private final FCMService fcmService;
    private final NotificationPreferencesRepository preferencesRepository;
    private final NotificationStatsRepository notificationStatsRepository;

    public void sendNotification(
            User user,
            String messageKey,
            NotificationCategory category,
            Map<String, Object> variables,
            Function<NotificationMessage, String> messageProvider) {

        NotificationPreferences prefs = getPreferences(user);
        UserProfile profile = user.getProfile();

        if (profile == null) {
            log.warn("User {} has no profile, skipping notification", user.getId());
            return;
        }

        // Check if category is enabled and not in DND
        if (!prefs.isCategoryEnabled(category) || prefs.isDndActive()) {
            log.debug("Notification skipped for user {} - Category disabled or DND active", user.getId());
            return;
        }

        // Process each enabled channel
        for (NotificationChannel channel : prefs.getEnabledChannels()) {
            try {
                NotificationMessage message = new NotificationMessage(channel, variables);
                String content = messageProvider.apply(message);

                if (content == null && channel != NotificationChannel.EMAIL) {
                    continue; // Skip if no content provided for this channel
                }

                sendToChannel(channel, user, profile, content, messageKey, variables);
            } catch (Exception e) {
                log.error("Failed to send notification through channel {} for user {}",
                        channel, user.getId(), e);
            }
        }
    }

    public void sendNotification(
            String email,
            String messageKey,
            NotificationCategory category,
            Map<String, Object> variables,
            Function<NotificationMessage, String> messageProvider) {
        // Only send through email channel
        try {
            NotificationMessage message = new NotificationMessage(NotificationChannel.EMAIL, variables);
            messageProvider.apply(message);
        } catch (Exception e) {
            log.error("Failed to send notification to email: {}", email, e);
        }
    }

    private void sendToChannel(
            NotificationChannel channel,
            User user,
            UserProfile profile,
            String content,
            String messageKey,
            Map<String, Object> variables) {

        switch (channel) {
            case EMAIL:
                // Email is handled by the messageProvider returning null
                break;
            case TELEGRAM:
                if (profile.getTelegramChatId() != null) {
                    telegramService.sendMessage(profile.getTelegramChatId(), content);
                }
                break;
            case WHATSAPP:
                if (profile.getPhone() != null) {
                    whatsAppService.sendMessage(profile.getPhone(), content);
                }
                break;
            case SMS:
                if (profile.getPhone() != null) {
                    smsService.sendMessage(profile.getPhone(), content);
                }
                break;
            case FCM:
                if (user.getFcmToken() != null) {
                    String title = (String) variables.getOrDefault("title", messageKey);
                    sendFCMNotification(user.getFcmToken(), title, content, variables);
                }
                break;
        }
    }

    public NotificationPreferences getPreferences(User user) {
        return preferencesRepository.findByUser(user)
                .orElseGet(() -> {
                    NotificationPreferences prefs = NotificationPreferences.createDefault(user);
                    return preferencesRepository.save(prefs);
                });
    }

    // Helper class for message formatting
    public static class NotificationMessage {
        private final NotificationChannel channel;
        private final Map<String, Object> variables;

        public NotificationMessage(NotificationChannel channel, Map<String, Object> variables) {
            this.channel = channel;
            this.variables = variables;
        }

        public NotificationChannel getChannel() {
            return channel;
        }

        public Map<String, Object> getVariables() {
            return variables;
        }
    }

    private NotificationPreferences getOrCreatePreferences(User user) {
        return preferencesRepository.findByUser(user)
                .orElseGet(() -> {
                    NotificationPreferences prefs = NotificationPreferences.createDefault(user);
                    return preferencesRepository.save(prefs);
                });
    }

    public NotificationPreferences updateNotificationPreferences(
            User user,
            Set<NotificationChannel> enabledChannels,
            Set<NotificationCategory> enabledCategories,
            boolean dndEnabled,
            LocalTime dndStartTime,
            LocalTime dndEndTime,
            String timezone) {

        NotificationPreferences preferences = getOrCreatePreferences(user);

        preferences.setEnabledChannels(enabledChannels);
        preferences.setEnabledCategories(enabledCategories);
        preferences.setDndEnabled(dndEnabled);
        preferences.setDndStartTime(dndStartTime);
        preferences.setDndEndTime(dndEndTime);
        preferences.setTimezone(timezone);

        return preferencesRepository.save(preferences);
    }

    public Page<NotificationPreferencesWithUser> getAllUserPreferences(PageRequest pageable) {
        Page<NotificationPreferences> preferencesPage = preferencesRepository.findAll(pageable);

        return preferencesPage.map(prefs -> {
            User user = prefs.getUser();
            return new NotificationPreferencesWithUser(
                    Integer.valueOf(user.getId()),
                    user.getEmail(),
                    prefs);
        });
    }

    public NotificationStats getNotificationStats() {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(30); // Get stats for the last 30 days

        List<com.orbvpn.api.domain.entity.NotificationStats> statsList = notificationStatsRepository
                .findByStatDateBetween(startDate, today);

        NotificationStats stats = new NotificationStats();
        // Populate the stats object based on the retrieved data
        // This is a simplified example, you may need to adjust based on your exact
        // requirements
        stats.setTotalNotificationsSent(statsList.stream().mapToInt(s -> s.getSentCount()).sum());
        stats.setFailedNotifications(statsList.stream().mapToInt(s -> s.getFailCount()).sum());
        // Populate notificationsByChannel and notificationsByCategory
        // You'll need to implement this based on your data structure and requirements

        return stats;
    }

    private void sendFCMNotification(
            String fcmToken,
            String title,
            String body,
            Map<String, Object> data) {
        try {
            // Create a new map for FCM data to avoid modifying the original
            Map<String, String> fcmData = new HashMap<>();

            // Add required FCM specific fields
            fcmData.put("click_action", "FLUTTER_NOTIFICATION_CLICK");
            fcmData.put("title", title);
            fcmData.put("body", body);

            // Add any additional data fields
            if (data != null) {
                data.forEach((key, value) -> {
                    if (value != null) {
                        fcmData.put(key, value.toString());
                    }
                });
            }

            NotificationDto notificationDto = NotificationDto.builder()
                    .subject(title)
                    .content(body)
                    .data(fcmData)
                    .build();

            fcmService.sendNotification(notificationDto, fcmToken);
            log.debug("FCM notification sent successfully to token: {}", fcmToken);
        } catch (Exception e) {
            log.error("Failed to send FCM notification to token: {}", fcmToken, e);
        }
    }
}