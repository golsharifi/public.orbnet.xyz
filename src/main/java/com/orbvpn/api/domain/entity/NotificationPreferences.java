package com.orbvpn.api.domain.entity;

import com.orbvpn.api.domain.enums.NotificationChannel;
import com.orbvpn.api.domain.enums.NotificationCategory;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "notification_preferences", indexes = {
        @Index(name = "idx_notification_prefs_user_id", columnList = "user_id"),
        @Index(name = "idx_notification_prefs_enabled", columnList = "dnd_enabled")
})
@EqualsAndHashCode(exclude = { "user" })
@ToString(exclude = { "user" })
public class NotificationPreferences {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    @NotNull
    private User user;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "notification_preferences_enabled_channels", joinColumns = @JoinColumn(name = "notification_preferences_id"), indexes = @Index(name = "idx_enabled_channels", columnList = "notification_preferences_id"))
    @Column(name = "enabled_channels", length = 255)
    @Enumerated(EnumType.STRING)
    private Set<NotificationChannel> enabledChannels = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "notification_preferences_enabled_categories", joinColumns = @JoinColumn(name = "notification_preferences_id"), indexes = @Index(name = "idx_enabled_categories", columnList = "notification_preferences_id"))
    @Column(name = "enabled_categories", length = 255)
    @Enumerated(EnumType.STRING)
    private Set<NotificationCategory> enabledCategories = new HashSet<>();

    @Column(name = "dnd_enabled", nullable = false)
    private boolean dndEnabled = false;

    @Column(name = "email_unsubscribed", nullable = false)
    private boolean emailUnsubscribed = false;

    @Column(name = "unsubscribed_at")
    private LocalDateTime unsubscribedAt;

    @Column(name = "resubscribed_at")
    private LocalDateTime resubscribedAt;

    @Column(name = "dnd_start_time")
    private LocalTime dndStartTime;

    @Column(name = "dnd_end_time")
    private LocalTime dndEndTime;

    @Column(name = "timezone", length = 50, nullable = false)
    private String timezone = "UTC";

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isChannelEnabled(NotificationChannel channel) {
        return enabledChannels != null && enabledChannels.contains(channel);
    }

    public boolean isCategoryEnabled(NotificationCategory category) {
        return enabledCategories != null && enabledCategories.contains(category);
    }

    public void enableChannel(NotificationChannel channel) {
        if (enabledChannels == null) {
            enabledChannels = new HashSet<>();
        }
        enabledChannels.add(channel);
    }

    public void disableChannel(NotificationChannel channel) {
        if (enabledChannels != null) {
            enabledChannels.remove(channel);
        }
    }

    public void enableCategory(NotificationCategory category) {
        if (enabledCategories == null) {
            enabledCategories = new HashSet<>();
        }
        enabledCategories.add(category);
    }

    public void disableCategory(NotificationCategory category) {
        if (enabledCategories != null) {
            enabledCategories.remove(category);
        }
    }

    public boolean isDndActive() {
        if (!dndEnabled || dndStartTime == null || dndEndTime == null) {
            return false;
        }

        try {
            LocalTime now = LocalTime.now(ZoneId.of(timezone));
            if (dndStartTime.equals(dndEndTime)) {
                return false; // Same start and end time means DND is effectively disabled
            }

            if (dndStartTime.isBefore(dndEndTime)) {
                return now.isAfter(dndStartTime) && now.isBefore(dndEndTime);
            } else {
                // Handles cases where DND spans midnight
                return now.isAfter(dndStartTime) || now.isBefore(dndEndTime);
            }
        } catch (Exception e) {
            // Log error and default to false if timezone is invalid
            return false;
        }
    }

    public static NotificationPreferences createDefault(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }

        NotificationPreferences prefs = new NotificationPreferences();
        prefs.setUser(user);
        prefs.setEnabledChannels(new HashSet<>());
        prefs.setEnabledCategories(new HashSet<>(Arrays.asList(NotificationCategory.values())));
        prefs.setTimezone("UTC");

        // Enable default channels based on user profile
        prefs.enableChannel(NotificationChannel.EMAIL);
        prefs.enableChannel(NotificationChannel.FCM);

        if (user.getProfile() != null) {
            UserProfile profile = user.getProfile();
            if (profile.getPhone() != null && !profile.getPhone().trim().isEmpty()) {
                prefs.enableChannel(NotificationChannel.WHATSAPP);
                // prefs.enableChannel(NotificationChannel.SMS);
            }
            if (profile.getTelegramChatId() != null && !profile.getTelegramChatId().trim().isEmpty()) {
                prefs.enableChannel(NotificationChannel.TELEGRAM);
            }
        }

        return prefs;
    }

    /**
     * Unsubscribe from all email notifications.
     */
    public void unsubscribeFromEmail() {
        this.emailUnsubscribed = true;
        this.unsubscribedAt = LocalDateTime.now();
        disableChannel(NotificationChannel.EMAIL);
    }

    /**
     * Resubscribe to email notifications.
     */
    public void resubscribeToEmail() {
        this.emailUnsubscribed = false;
        this.resubscribedAt = LocalDateTime.now();
        enableChannel(NotificationChannel.EMAIL);
    }

    /**
     * Check if user can receive emails.
     * Returns false if globally unsubscribed or email channel is disabled.
     */
    public boolean canReceiveEmail() {
        return !emailUnsubscribed && isChannelEnabled(NotificationChannel.EMAIL);
    }

    /**
     * Check if user can receive a specific category of notifications via email.
     */
    public boolean canReceiveEmailForCategory(NotificationCategory category) {
        return canReceiveEmail() && isCategoryEnabled(category);
    }
}
