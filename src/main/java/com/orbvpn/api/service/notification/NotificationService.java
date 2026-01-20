package com.orbvpn.api.service.notification;

import com.orbvpn.api.domain.entity.UserProfile;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.domain.enums.NotificationCategory;
import com.orbvpn.api.repository.UserProfileRepository;
import com.orbvpn.api.repository.UserRepository;
import com.orbvpn.api.service.subscription.SubscriptionQueryService;
import com.orbvpn.api.domain.entity.GiftCard;
import com.orbvpn.api.domain.entity.UserExtraLogins;
import com.orbvpn.api.service.subscription.UserSubscriptionService;
import com.orbvpn.api.domain.dto.NotificationDto;

import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.MessageSource;
import org.springframework.scheduling.annotation.Scheduled;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import java.time.temporal.ChronoUnit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@EnableScheduling
@Slf4j
@RequiredArgsConstructor
public class NotificationService implements NotificationOperations {
        private final EmailService emailService;
        private final MultiChannelNotificationService multiChannelService;
        private final MessageSource messageSource;
        private final UserProfileRepository userProfileRepository;
        private final UserRepository userRepository;
        private final SubscriptionQueryService subscriptionQueryService;
        private final LocaleResolverService localeResolverService;
        private final ObjectMapper objectMapper;
        private final UserSubscriptionService userSubscriptionService;
        private final SmsService smsService;
        private final WhatsAppService whatsAppService;
        private final TelegramService telegramService;
        private final FCMService fcmService;

        @Value("${app.api.base-url:https://api.orbvpn.com}")
        private String apiBaseUrl;

        // Staggered notification schedules to avoid concurrent execution
        private static final String BIRTHDAY_SCHEDULE = "0 0 8 * * ?";      // 8:00 AM
        private static final String EXPIRATION_REMINDER_SCHEDULE = "0 15 8 * * ?"; // 8:15 AM
        private static final String POST_EXPIRATION_SCHEDULE = "0 30 8 * * ?";     // 8:30 AM
        private static final int[] DAYS_BEFORE_EXPIRATION = { 7, 3, 1 };
        private static final int[] DAYS_AFTER_EXPIRATION = { 1, 3, 7 };

        /**
         * Sends a notification to all admin users
         * 
         * @param message  The message content
         * @param title    The notification title
         * @param metadata Additional metadata for the notification
         */
        public void sendAdminNotification(String message, String title, Map<String, String> metadata) {
                try {
                        Map<String, Object> variables = new HashMap<>(metadata);
                        variables.put("message", message);
                        variables.put("title", title);
                        variables.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

                        // Use the new method to get admin users
                        List<User> adminUsers = userRepository.findAdminUsers();

                        for (User admin : adminUsers) {
                                Locale locale = localeResolverService.resolveUserLocale(admin);

                                multiChannelService.sendNotification(
                                                admin,
                                                "admin-alert",
                                                NotificationCategory.SYSTEM,
                                                variables,
                                                notificationMessage -> {
                                                        switch (notificationMessage.getChannel()) {
                                                                case EMAIL:
                                                                        emailService.sendTemplatedEmail(
                                                                                        admin.getEmail(),
                                                                                        "admin-alert",
                                                                                        variables,
                                                                                        locale);
                                                                        return null;

                                                                case FCM:
                                                                        Map<String, Object> fcmNotification = new HashMap<>();
                                                                        fcmNotification.put("title", title);
                                                                        fcmNotification.put("body", message);
                                                                        fcmNotification.put("type", "admin_alert");
                                                                        fcmNotification.put("click_action",
                                                                                        "FLUTTER_NOTIFICATION_CLICK");
                                                                        fcmNotification.put("metadata", metadata);

                                                                        try {
                                                                                return objectMapper.writeValueAsString(
                                                                                                fcmNotification);
                                                                        } catch (JsonProcessingException e) {
                                                                                log.error("Error serializing FCM admin notification",
                                                                                                e);
                                                                                return null;
                                                                        }

                                                                case TELEGRAM:
                                                                        return String.format("⚠️ *%s*\n\n%s", title,
                                                                                        message);

                                                                case WHATSAPP:
                                                                        return String.format("*%s*\n\n%s", title,
                                                                                        message);

                                                                case SMS:
                                                                        return String.format("%s: %s", title, message);

                                                                default:
                                                                        return message;
                                                        }
                                                });
                        }

                        log.info("Sent admin notification: {}", title);
                } catch (Exception e) {
                        log.error("Failed to send admin notification", e);
                }
        }

        // Birthday Notification
        @Override
        @Scheduled(cron = BIRTHDAY_SCHEDULE)
        @SchedulerLock(name = "sendBirthdayWish", lockAtLeastFor = "5m", lockAtMostFor = "30m")
        public void sendBirthdayWish() {
                log.info("Sending birthday notifications...");
                List<UserProfile> userProfiles = userProfileRepository.findUsersBornToday();

                for (UserProfile userProfile : userProfiles) {
                        try {
                                User user = userProfile.getUser();
                                Locale locale = localeResolverService.resolveUserLocale(user);
                                String userName = getUserDisplayName(user);

                                // Prepare common variables
                                Map<String, Object> variables = new HashMap<>();
                                variables.put("userName", userName);

                                // Send through multi-channel service
                                multiChannelService.sendNotification(
                                                user,
                                                "birthday-wish",
                                                NotificationCategory.PROMOTIONAL,
                                                variables,
                                                message -> {
                                                        switch (message.getChannel()) {
                                                                case WHATSAPP:
                                                                        return messageSource.getMessage(
                                                                                        "whatsapp.birthday.message",
                                                                                        new Object[] { userName },
                                                                                        locale);
                                                                case TELEGRAM:
                                                                        return messageSource.getMessage(
                                                                                        "telegram.birthday.message",
                                                                                        new Object[] { userName },
                                                                                        locale);
                                                                case SMS:
                                                                        return messageSource.getMessage(
                                                                                        "sms.birthday.wish",
                                                                                        new Object[] { userName },
                                                                                        locale);
                                                                case FCM:
                                                                        Map<String, Object> fcmNotification = new HashMap<>();
                                                                        fcmNotification.put("title",
                                                                                        messageSource.getMessage(
                                                                                                        "fcm.birthday.title",
                                                                                                        null,
                                                                                                        locale));
                                                                        fcmNotification.put("body",
                                                                                        messageSource.getMessage(
                                                                                                        "fcm.birthday.body",
                                                                                                        new Object[] { userName },
                                                                                                        locale));
                                                                        fcmNotification.put("type",
                                                                                        "birthday_notification");
                                                                        fcmNotification.put("click_action",
                                                                                        "FLUTTER_NOTIFICATION_CLICK"); // Required
                                                                                                                       // for
                                                                                                                       // Flutter
                                                                        fcmNotification.put("custom_action",
                                                                                        "OPEN_DASHBOARD"); // Added
                                                                                                           // custom
                                                                                                           // action
                                                                        fcmNotification.put("data", Map.of( // Added
                                                                                                            // data map
                                                                                        "userName", userName));
                                                                        try {
                                                                                return new ObjectMapper()
                                                                                                .writeValueAsString(
                                                                                                                fcmNotification);
                                                                        } catch (JsonProcessingException e) {
                                                                                log.error("Error serializing FCM birthday notification message",
                                                                                                e);
                                                                                return null;
                                                                        }
                                                                case EMAIL:
                                                                default:
                                                                        // Use existing email template
                                                                        emailService.sendTemplatedEmail(
                                                                                        user.getEmail(),
                                                                                        "birthday-wish",
                                                                                        variables,
                                                                                        locale);
                                                                        return null;
                                                        }
                                                });

                                log.debug("Sent birthday notifications to user: {}", user.getEmail());
                        } catch (Exception e) {
                                log.error("Failed to send birthday notifications to user: {}",
                                                userProfile.getUser().getEmail(), e);
                        }
                }
        }

        @Override
        @Scheduled(cron = POST_EXPIRATION_SCHEDULE)
        @SchedulerLock(name = "afterSubscriptionExpiredNotification", lockAtLeastFor = "5m", lockAtMostFor = "30m")
        public void afterSubscriptionExpiredNotification() {
                log.info("Sending after subscription expiration notifications...");
                for (Integer dayCount : DAYS_AFTER_EXPIRATION) {
                        List<UserProfile> userProfiles = subscriptionQueryService
                                        .getUsersExpireInPreviousDays(dayCount);
                        for (UserProfile userProfile : userProfiles) {
                                try {
                                        User user = userProfile.getUser();
                                        Locale locale = localeResolverService.resolveUserLocale(user);
                                        String userName = getUserDisplayName(user);

                                        UserSubscription userSubscription = userSubscriptionService
                                                        .getCurrentSubscription(user);
                                        if (userSubscription == null) {
                                                log.warn("No subscription found for user: {}", user.getEmail());
                                                continue;
                                        }

                                        long actualDays = ChronoUnit.DAYS.between(userSubscription.getExpiresAt(),
                                                        LocalDateTime.now());
                                        log.debug("Processing expired notification - User: {}, Days since expiration: {}, Locale: {}",
                                                        user.getEmail(), actualDays, locale);

                                        Map<String, Object> variables = new HashMap<>();
                                        variables.put("userName", userName);
                                        variables.put("daysAgo", actualDays);
                                        variables.put("mailtoLink", createMailtoLink(
                                                        "Expired Subscription Renewal",
                                                        String.format("My subscription expired %d day(s) ago. I would like to renew it.",
                                                                        actualDays)));

                                        multiChannelService.sendNotification(
                                                        user,
                                                        "subscription-expired",
                                                        NotificationCategory.BILLING,
                                                        variables,
                                                        message -> {
                                                                switch (message.getChannel()) {
                                                                        case SMS:
                                                                                return messageSource.getMessage(
                                                                                                "sms.subscription.expired",
                                                                                                new Object[] {
                                                                                                                actualDays,
                                                                                                                actualDays == 1 ? messageSource
                                                                                                                                .getMessage("common.day",
                                                                                                                                                null,
                                                                                                                                                locale)
                                                                                                                                : messageSource.getMessage(
                                                                                                                                                "common.days",
                                                                                                                                                null,
                                                                                                                                                locale)
                                                                                                },
                                                                                                locale);
                                                                        case WHATSAPP:
                                                                                return messageSource.getMessage(
                                                                                                "whatsapp.subscription.expired",
                                                                                                new Object[] { userName,
                                                                                                                actualDays },
                                                                                                locale);
                                                                        case TELEGRAM:
                                                                                return messageSource.getMessage(
                                                                                                "telegram.subscription.expired",
                                                                                                new Object[] { userName,
                                                                                                                actualDays },
                                                                                                locale);
                                                                        case FCM:
                                                                                Map<String, Object> fcmData = new HashMap<>();
                                                                                fcmData.put("title", messageSource
                                                                                                .getMessage(
                                                                                                                "fcm.subscription.expired.title",
                                                                                                                null,
                                                                                                                locale));
                                                                                fcmData.put("body", messageSource
                                                                                                .getMessage(
                                                                                                                "fcm.subscription.expired.body",
                                                                                                                new Object[] { actualDays },
                                                                                                                locale));
                                                                                fcmData.put("type",
                                                                                                "subscription_expired");
                                                                                fcmData.put("click_action",
                                                                                                "FLUTTER_NOTIFICATION_CLICK");
                                                                                fcmData.put("custom_action",
                                                                                                "RENEW_SUBSCRIPTION");
                                                                                fcmData.put("data", Map.of("daysAgo",
                                                                                                actualDays));
                                                                                try {
                                                                                        return objectMapper
                                                                                                        .writeValueAsString(
                                                                                                                        fcmData);
                                                                                } catch (JsonProcessingException e) {
                                                                                        log.error("Error serializing FCM notification for expired subscription",
                                                                                                        e);
                                                                                        return null;
                                                                                }
                                                                        case EMAIL:
                                                                        default:
                                                                                emailService.sendTemplatedEmail(
                                                                                                user.getEmail(),
                                                                                                "subscription-expired",
                                                                                                variables,
                                                                                                locale);
                                                                                return null;
                                                                }
                                                        });

                                        log.debug("Sent expired notification to user: {}", user.getEmail());
                                } catch (Exception e) {
                                        log.error("Failed to send expired notification to user: {}",
                                                        userProfile.getUser().getEmail(), e);
                                }
                        }
                }
        }

        @Override
        @Scheduled(cron = EXPIRATION_REMINDER_SCHEDULE)
        @SchedulerLock(name = "subscriptionExpirationReminder", lockAtLeastFor = "5m", lockAtMostFor = "30m")
        public void subscriptionExpirationReminder() {
                log.info("Sending subscription expiration reminders...");
                for (Integer dayCount : DAYS_BEFORE_EXPIRATION) {
                        List<UserProfile> userProfiles = subscriptionQueryService.getUsersExpireInNextDays(dayCount);
                        for (UserProfile userProfile : userProfiles) {
                                try {
                                        User user = userProfile.getUser();
                                        Locale locale = localeResolverService.resolveUserLocale(user);
                                        String userName = getUserDisplayName(user);

                                        log.debug("Processing expiration reminder - User: {}, Days remaining: {}, Locale: {}",
                                                        user.getEmail(), dayCount, locale);

                                        Map<String, Object> variables = new HashMap<>();
                                        variables.put("userName", userName);
                                        variables.put("daysRemaining", dayCount);
                                        variables.put("mailtoLink", createMailtoLink(
                                                        "Subscription Renewal Request",
                                                        String.format("My subscription is expiring in %d day(s). I would like to renew it.",
                                                                        dayCount)));

                                        multiChannelService.sendNotification(
                                                        user,
                                                        "subscription-expiry-reminder",
                                                        NotificationCategory.BILLING,
                                                        variables,
                                                        message -> {
                                                                switch (message.getChannel()) {
                                                                        case SMS:
                                                                                return messageSource.getMessage(
                                                                                                "sms.subscription.expiry",
                                                                                                new Object[] {
                                                                                                                dayCount,
                                                                                                                dayCount == 1 ? messageSource
                                                                                                                                .getMessage("common.day",
                                                                                                                                                null,
                                                                                                                                                locale)
                                                                                                                                : messageSource.getMessage(
                                                                                                                                                "common.days",
                                                                                                                                                null,
                                                                                                                                                locale)
                                                                                                },
                                                                                                locale);
                                                                        case WHATSAPP:
                                                                                return messageSource.getMessage(
                                                                                                "whatsapp.subscription.expiry",
                                                                                                new Object[] { userName,
                                                                                                                dayCount },
                                                                                                locale);
                                                                        case TELEGRAM:
                                                                                return messageSource.getMessage(
                                                                                                "telegram.subscription.expiry",
                                                                                                new Object[] { userName,
                                                                                                                dayCount },
                                                                                                locale);
                                                                        case FCM:
                                                                                Map<String, Object> fcmData = new HashMap<>();
                                                                                fcmData.put("title", messageSource
                                                                                                .getMessage(
                                                                                                                "fcm.subscription.expiry.title",
                                                                                                                null,
                                                                                                                locale));
                                                                                fcmData.put("body", messageSource
                                                                                                .getMessage(
                                                                                                                "fcm.subscription.expiry.body",
                                                                                                                new Object[] { dayCount },
                                                                                                                locale));
                                                                                fcmData.put("type",
                                                                                                "subscription_expiry");
                                                                                fcmData.put("click_action",
                                                                                                "FLUTTER_NOTIFICATION_CLICK");
                                                                                fcmData.put("custom_action",
                                                                                                "RENEW_SUBSCRIPTION");
                                                                                fcmData.put("data", Map.of(
                                                                                                "daysRemaining",
                                                                                                dayCount));
                                                                                try {
                                                                                        return objectMapper
                                                                                                        .writeValueAsString(
                                                                                                                        fcmData);
                                                                                } catch (JsonProcessingException e) {
                                                                                        log.error("Error serializing FCM notification for expiry subscription",
                                                                                                        e);
                                                                                        return null;
                                                                                }
                                                                        case EMAIL:
                                                                        default:
                                                                                emailService.sendTemplatedEmail(
                                                                                                user.getEmail(),
                                                                                                "subscription-expiry-reminder",
                                                                                                variables,
                                                                                                locale);
                                                                                return null;
                                                                }
                                                        });

                                        log.debug("Sent expiration reminder to user: {}", user.getEmail());
                                } catch (Exception e) {
                                        log.error("Failed to send expiration reminder to user: {}",
                                                        userProfile.getUser().getEmail(), e);
                                }
                        }
                }
        }

        // Welcoming New Users Created By Admin
        @Override
        public void welcomingNewUsersCreatedByAdmin(User user, UserSubscription subscription, String password) {
                // Delegate to the safe method to avoid lambda class loading issues
                sendWelcomeNotificationSafely(user, subscription, password);
        }

        // New safe method that doesn't use lambda expressions
        public void sendWelcomeNotificationSafely(User user, UserSubscription subscription, String password) {
                try {
                        Locale locale = localeResolverService.resolveUserLocale(user);
                        String userName = getUserDisplayName(user);

                        // Send notifications directly without lambda expressions
                        sendWelcomeEmail(user, userName, password, subscription, locale);
                        sendWelcomeSMS(user, password, locale);
                        sendWelcomeWhatsApp(user, userName, password, locale);
                        sendWelcomeTelegram(user, userName, password, locale);
                        sendWelcomeFCM(user, userName, subscription, locale);

                        log.debug("Sent welcome notifications to new user: {}", user.getEmail());
                } catch (Exception e) {
                        log.error("Failed to send welcome notifications to user: {}", user.getEmail(), e);
                }
        }

        private void sendWelcomeEmail(User user, String userName, String password, UserSubscription subscription,
                        Locale locale) {
                try {
                        Map<String, Object> variables = new HashMap<>();
                        variables.put("userName", userName);
                        variables.put("username", user.getUsername());
                        variables.put("password", password);
                        variables.put("subscription", subscription);
                        variables.put("duration", subscription.getGroup().getDuration());
                        variables.put("devices", subscription.getMultiLoginCount());
                        variables.put("formattedExpiry", subscription.getExpiresAt().format(
                                        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")));

                        emailService.sendTemplatedEmail(user.getEmail(), "welcome-new-user", variables, locale);
                } catch (Exception e) {
                        log.error("Failed to send welcome email: {}", e.getMessage(), e);
                }
        }

        private void sendWelcomeSMS(User user, String password, Locale locale) {
                try {
                        if (user.getProfile() != null && user.getProfile().getPhone() != null) {
                                String message = messageSource.getMessage(
                                                "sms.welcome.new.user",
                                                new Object[] { user.getUsername(), password },
                                                locale);
                                smsService.sendMessage(user.getProfile().getPhone(), message);
                        }
                } catch (Exception e) {
                        log.error("Failed to send welcome SMS: {}", e.getMessage(), e);
                }
        }

        private void sendWelcomeWhatsApp(User user, String userName, String password, Locale locale) {
                try {
                        if (user.getProfile() != null && user.getProfile().getPhone() != null) {
                                String message = messageSource.getMessage(
                                                "whatsapp.welcome.new.user",
                                                new Object[] { userName, user.getUsername(), password },
                                                locale);
                                whatsAppService.sendMessage(user.getProfile().getPhone(), message);
                        }
                } catch (Exception e) {
                        log.error("Failed to send welcome WhatsApp: {}", e.getMessage(), e);
                }
        }

        private void sendWelcomeTelegram(User user, String userName, String password, Locale locale) {
                try {
                        if (user.getProfile() != null && user.getProfile().getTelegramChatId() != null) {
                                String message = messageSource.getMessage(
                                                "telegram.welcome.new.user",
                                                new Object[] { userName, user.getUsername(), password },
                                                locale);
                                telegramService.sendMessage(user.getProfile().getTelegramChatId(), message);
                        }
                } catch (Exception e) {
                        log.error("Failed to send welcome Telegram: {}", e.getMessage(), e);
                }
        }

        private void sendWelcomeFCM(User user, String userName, UserSubscription subscription, Locale locale) {
                try {
                        if (user.getFcmToken() != null) {
                                String title = messageSource.getMessage("fcm.welcome.new.user.title", null, locale);
                                String body = messageSource.getMessage("fcm.welcome.new.user.body",
                                                new Object[] { userName }, locale);

                                Map<String, Object> data = Map.of(
                                                "userName", userName,
                                                "duration", subscription.getGroup().getDuration(),
                                                "devices", subscription.getMultiLoginCount(),
                                                "expiryDate",
                                                subscription.getExpiresAt().format(DateTimeFormatter.ISO_DATE_TIME),
                                                "type", "welcome",
                                                "click_action", "FLUTTER_NOTIFICATION_CLICK",
                                                "custom_action", "OPEN_DASHBOARD");

                                NotificationDto notificationDto = NotificationDto.builder()
                                                .subject(title)
                                                .content(body)
                                                .data(data.entrySet().stream().collect(Collectors.toMap(
                                                                Map.Entry::getKey,
                                                                e -> e.getValue().toString())))
                                                .build();

                                fcmService.sendNotification(notificationDto, user.getFcmToken());
                        }
                } catch (Exception e) {
                        log.error("Failed to send welcome FCM: {}", e.getMessage(), e);
                }
        }

        // Reset Password Notification
        @Override
        public void resetPassword(User user, String token) {
                try {
                        Locale locale = localeResolverService.resolveUserLocale(user);
                        String userName = getUserDisplayName(user);

                        Map<String, Object> variables = new HashMap<>();
                        variables.put("userName", userName);
                        variables.put("token", token);
                        variables.put("mailtoLink", createMailtoLink(
                                        "Unauthorized Password Reset",
                                        "I did not request a password reset."));

                        multiChannelService.sendNotification(
                                        user,
                                        "password-reset",
                                        NotificationCategory.SECURITY,
                                        variables,
                                        message -> {
                                                switch (message.getChannel()) {
                                                        case SMS:
                                                                return messageSource.getMessage(
                                                                                "sms.password.reset",
                                                                                new Object[] { token },
                                                                                locale);
                                                        case WHATSAPP:
                                                                return messageSource.getMessage(
                                                                                "whatsapp.password.reset",
                                                                                new Object[] { userName, token },
                                                                                locale);
                                                        case TELEGRAM:
                                                                return messageSource.getMessage(
                                                                                "telegram.password.reset",
                                                                                new Object[] { userName, token },
                                                                                locale);

                                                        case EMAIL:
                                                        default:
                                                                emailService.sendTemplatedEmail(
                                                                                user.getEmail(),
                                                                                "password-reset",
                                                                                variables,
                                                                                locale);
                                                                return null;
                                                }
                                        });

                        log.debug("Sent password reset notifications to user: {}", user.getEmail());
                } catch (Exception e) {
                        log.error("Failed to send password reset notifications to user: {}",
                                        user.getEmail(), e);
                }
        }

        // Reset Password Done Notification
        @Override
        public void resetPasswordDone(User user) {
                try {
                        Locale locale = localeResolverService.resolveUserLocale(user);
                        String userName = getUserDisplayName(user);

                        Map<String, Object> variables = new HashMap<>();
                        variables.put("userName", userName);
                        variables.put("mailtoLink", createMailtoLink(
                                        "Unauthorized Password Change",
                                        "I did not change my password."));

                        multiChannelService.sendNotification(
                                        user,
                                        "password-reset-done",
                                        NotificationCategory.SECURITY,
                                        variables,
                                        message -> {
                                                switch (message.getChannel()) {
                                                        case SMS:
                                                                return messageSource.getMessage(
                                                                                "sms.password.reset.done",
                                                                                null,
                                                                                locale);
                                                        case WHATSAPP:
                                                                return messageSource.getMessage(
                                                                                "whatsapp.password.reset.done",
                                                                                new Object[] { userName },
                                                                                locale);
                                                        case TELEGRAM:
                                                                return messageSource.getMessage(
                                                                                "telegram.password.reset.done",
                                                                                new Object[] { userName },
                                                                                locale);

                                                        case FCM:
                                                                Map<String, Object> fcmPasswordReset = new HashMap<>();
                                                                fcmPasswordReset.put("title", messageSource.getMessage(
                                                                                "fcm.password.reset.done.title",
                                                                                null,
                                                                                locale));
                                                                fcmPasswordReset.put("body", messageSource.getMessage(
                                                                                "fcm.password.reset.done.body",
                                                                                null,
                                                                                locale));
                                                                fcmPasswordReset.put("type", "password_reset_complete");
                                                                fcmPasswordReset.put("click_action",
                                                                                "FLUTTER_NOTIFICATION_CLICK"); // Required
                                                                                                               // for
                                                                                                               // Flutter
                                                                fcmPasswordReset.put("custom_action", "OPEN_LOGIN"); // Your
                                                                                                                     // custom
                                                                                                                     // action
                                                                try {
                                                                        return new ObjectMapper().writeValueAsString(
                                                                                        fcmPasswordReset);
                                                                } catch (JsonProcessingException e) {
                                                                        log.error("Error serializing FCM password reset message",
                                                                                        e);
                                                                        return null;
                                                                }

                                                        case EMAIL:
                                                        default:
                                                                emailService.sendTemplatedEmail(
                                                                                user.getEmail(),
                                                                                "password-reset-done",
                                                                                variables,
                                                                                locale);
                                                                return null;
                                                }
                                        });
                        log.debug("Sent password reset completion notifications to user: {}", user.getEmail());
                } catch (Exception e) {
                        log.error("Failed to send password reset completion notifications to user: {}",
                                        user.getEmail(), e);
                }
        }

        // Admin Password Reset Notification (when admin/reseller resets user's password)
        @Override
        public void sendAdminPasswordResetNotification(User user, String newPassword) {
                try {
                        Locale locale = localeResolverService.resolveUserLocale(user);
                        String userName = getUserDisplayName(user);

                        Map<String, Object> variables = new HashMap<>();
                        variables.put("userName", userName);
                        variables.put("password", newPassword);

                        multiChannelService.sendNotification(
                                        user,
                                        "password-reset-admin",
                                        NotificationCategory.SECURITY,
                                        variables,
                                        message -> {
                                                switch (message.getChannel()) {
                                                        case SMS:
                                                                return messageSource.getMessage(
                                                                                "sms.password.reset.admin",
                                                                                new Object[] { newPassword },
                                                                                locale);
                                                        case WHATSAPP:
                                                                return messageSource.getMessage(
                                                                                "whatsapp.password.reset.admin",
                                                                                new Object[] { userName, newPassword },
                                                                                locale);
                                                        case TELEGRAM:
                                                                return messageSource.getMessage(
                                                                                "telegram.password.reset.admin",
                                                                                new Object[] { userName, newPassword },
                                                                                locale);

                                                        case FCM:
                                                                Map<String, Object> fcmData = new HashMap<>();
                                                                fcmData.put("title", messageSource.getMessage(
                                                                                "fcm.password.reset.admin.title",
                                                                                null,
                                                                                locale));
                                                                fcmData.put("body", messageSource.getMessage(
                                                                                "fcm.password.reset.admin.body",
                                                                                null,
                                                                                locale));
                                                                fcmData.put("type", "admin_password_reset");
                                                                fcmData.put("click_action",
                                                                                "FLUTTER_NOTIFICATION_CLICK");
                                                                fcmData.put("custom_action", "OPEN_LOGIN");
                                                                fcmData.put("data", Map.of(
                                                                                "userName", userName,
                                                                                "newPassword", newPassword));
                                                                try {
                                                                        return new ObjectMapper()
                                                                                        .writeValueAsString(fcmData);
                                                                } catch (JsonProcessingException e) {
                                                                        log.error("Error serializing FCM admin password reset message",
                                                                                        e);
                                                                        return null;
                                                                }

                                                        case EMAIL:
                                                        default:
                                                                emailService.sendTemplatedEmail(
                                                                                user.getEmail(),
                                                                                "password-reset-admin",
                                                                                variables,
                                                                                locale);
                                                                return null;
                                                }
                                        });
                        log.debug("Sent admin password reset notification to user: {}", user.getEmail());
                } catch (Exception e) {
                        log.error("Failed to send admin password reset notification to user: {}",
                                        user.getEmail(), e);
                }
        }

        // Send Gift Card Redemption Notification
        @Override
        public void sendGiftCardRedemptionNotification(User user, GiftCard giftCard) {
                try {
                        Locale locale = localeResolverService.resolveUserLocale(user);
                        String userName = getUserDisplayName(user);

                        // Create correctly mapped variables for the template
                        Map<String, Object> variables = new HashMap<>();
                        variables.put("userName", userName);
                        variables.put("giftCardCode", giftCard.getCode());
                        variables.put("groupName", giftCard.getGroup().getName());
                        variables.put("expirationDate", giftCard.getExpirationDate());

                        // Keep the full giftCard object for additional data if needed
                        variables.put("giftCard", giftCard);

                        multiChannelService.sendNotification(
                                        user,
                                        "gift-card-redemption",
                                        NotificationCategory.BILLING,
                                        variables,
                                        message -> {
                                                switch (message.getChannel()) {
                                                        case SMS:
                                                                return messageSource.getMessage(
                                                                                "sms.giftcard.redemption",
                                                                                new Object[] {
                                                                                                giftCard.getCode(),
                                                                                                giftCard.getGroup()
                                                                                                                .getName()
                                                                                },
                                                                                locale);
                                                        case WHATSAPP:
                                                                return messageSource.getMessage(
                                                                                "whatsapp.giftcard.redemption",
                                                                                new Object[] {
                                                                                                userName,
                                                                                                giftCard.getCode(),
                                                                                                giftCard.getGroup()
                                                                                                                .getName()
                                                                                },
                                                                                locale);
                                                        case TELEGRAM:
                                                                return messageSource.getMessage(
                                                                                "telegram.giftcard.redemption",
                                                                                new Object[] {
                                                                                                userName,
                                                                                                giftCard.getCode(),
                                                                                                giftCard.getGroup()
                                                                                                                .getName()
                                                                                },
                                                                                locale);
                                                        case FCM:
                                                                Map<String, Object> fcmGiftCard = new HashMap<>();
                                                                fcmGiftCard.put("title", messageSource.getMessage(
                                                                                "fcm.giftcard.redemption.title",
                                                                                null,
                                                                                locale));
                                                                fcmGiftCard.put("body", messageSource.getMessage(
                                                                                "fcm.giftcard.redemption.body",
                                                                                new Object[] {
                                                                                                giftCard.getCode(),
                                                                                                giftCard.getGroup()
                                                                                                                .getName()
                                                                                },
                                                                                locale));
                                                                fcmGiftCard.put("type", "giftcard_redemption");
                                                                fcmGiftCard.put("click_action",
                                                                                "FLUTTER_NOTIFICATION_CLICK");
                                                                fcmGiftCard.put("custom_action", "OPEN_SUBSCRIPTION");
                                                                fcmGiftCard.put("data", Map.of(
                                                                                "code", giftCard.getCode(),
                                                                                "plan", giftCard.getGroup().getName(),
                                                                                "validUntil",
                                                                                giftCard.getExpirationDate().format(
                                                                                                DateTimeFormatter.ISO_DATE_TIME)));
                                                                try {
                                                                        return new ObjectMapper().writeValueAsString(
                                                                                        fcmGiftCard);
                                                                } catch (JsonProcessingException e) {
                                                                        log.error("Error serializing FCM gift card redemption message",
                                                                                        e);
                                                                        return null;
                                                                }

                                                        case EMAIL:
                                                        default:
                                                                emailService.sendTemplatedEmail(
                                                                                user.getEmail(),
                                                                                "gift-card-redemption",
                                                                                variables,
                                                                                locale);
                                                                return null;
                                                }
                                        });
                        log.debug("Sent gift card redemption notifications to user: {}", user.getEmail());
                } catch (Exception e) {
                        log.error("Failed to send gift card redemption notifications to user: {}",
                                        user.getEmail(), e);
                }
        }

        // Send Extra Logins Confirmation
        @Override
        public void sendExtraLoginsConfirmation(User user, UserExtraLogins extraLogins) {
                try {
                        Locale locale = localeResolverService.resolveUserLocale(user);
                        String userName = getUserDisplayName(user);

                        Map<String, Object> variables = new HashMap<>();
                        variables.put("userName", userName);
                        variables.put("extraLogins", extraLogins);

                        multiChannelService.sendNotification(
                                        user,
                                        "extra-logins-confirmation",
                                        NotificationCategory.ACCOUNT,
                                        variables,
                                        message -> {
                                                String expiry = extraLogins.getExpiryDate() != null
                                                                ? messageSource.getMessage("until", null, locale) + " "
                                                                                + extraLogins.getExpiryDate()
                                                                : messageSource.getMessage("permanently", null, locale);

                                                switch (message.getChannel()) {
                                                        case SMS:
                                                                return messageSource.getMessage(
                                                                                "sms.extra.logins.confirmation",
                                                                                new Object[] { extraLogins
                                                                                                .getLoginCount(),
                                                                                                expiry },
                                                                                locale);
                                                        case WHATSAPP:
                                                                return messageSource.getMessage(
                                                                                "whatsapp.extra.logins.confirmation",
                                                                                new Object[] { userName, extraLogins
                                                                                                .getLoginCount(),
                                                                                                expiry },
                                                                                locale);
                                                        case TELEGRAM:
                                                                return messageSource.getMessage(
                                                                                "telegram.extra.logins.confirmation",
                                                                                new Object[] { userName, extraLogins
                                                                                                .getLoginCount(),
                                                                                                expiry },
                                                                                locale);

                                                        case FCM:
                                                                Map<String, Object> fcmExtraLogins = new HashMap<>();
                                                                fcmExtraLogins.put("title", messageSource.getMessage(
                                                                                "fcm.extra.logins.confirmation.title",
                                                                                null,
                                                                                locale));
                                                                fcmExtraLogins.put("body", messageSource.getMessage(
                                                                                "fcm.extra.logins.confirmation.body",
                                                                                new Object[] { extraLogins
                                                                                                .getLoginCount(),
                                                                                                expiry },
                                                                                locale));
                                                                fcmExtraLogins.put("type", "extra_logins_confirmation");
                                                                fcmExtraLogins.put("click_action",
                                                                                "FLUTTER_NOTIFICATION_CLICK"); // Required
                                                                                                               // for
                                                                                                               // Flutter
                                                                fcmExtraLogins.put("custom_action",
                                                                                "OPEN_ACCOUNT_SETTINGS"); // Your custom
                                                                                                          // action
                                                                fcmExtraLogins.put("data", Map.of( // Group additional
                                                                                                   // data
                                                                                "loginCount",
                                                                                extraLogins.getLoginCount(),
                                                                                "expiryDate",
                                                                                extraLogins.getExpiryDate() != null
                                                                                                ? extraLogins.getExpiryDate()
                                                                                                                .format(DateTimeFormatter.ISO_DATE_TIME)
                                                                                                : "permanent"));
                                                                try {
                                                                        return new ObjectMapper()
                                                                                        .writeValueAsString(
                                                                                                        fcmExtraLogins);
                                                                } catch (JsonProcessingException e) {
                                                                        log.error("Error serializing FCM extra logins confirmation message",
                                                                                        e);
                                                                        return null;
                                                                }

                                                        case EMAIL:
                                                        default:
                                                                emailService.sendTemplatedEmail(
                                                                                user.getEmail(),
                                                                                "extra-logins-confirmation",
                                                                                variables,
                                                                                locale);
                                                                return null;
                                                }
                                        });
                        log.debug("Sent extra logins confirmation to user: {}", user.getEmail());
                } catch (Exception e) {
                        log.error("Failed to send extra logins confirmation to user: {}", user.getEmail(), e);
                }
        }

        // Send Extra Logins Expiration Reminder
        @Override
        public void sendExtraLoginsExpirationReminder(User user, UserExtraLogins extraLogins, int daysRemaining) {
                try {
                        Locale locale = localeResolverService.resolveUserLocale(user);
                        String userName = getUserDisplayName(user);

                        Map<String, Object> variables = new HashMap<>();
                        variables.put("userName", userName);
                        variables.put("extraLogins", extraLogins);
                        variables.put("daysRemaining", daysRemaining);

                        multiChannelService.sendNotification(
                                        user,
                                        "extra-logins-expiration",
                                        NotificationCategory.ACCOUNT,
                                        variables,
                                        message -> {
                                                switch (message.getChannel()) {
                                                        case SMS:
                                                                return messageSource.getMessage(
                                                                                "sms.extra.logins.expiration",
                                                                                new Object[] {
                                                                                                extraLogins.getLoginCount(),
                                                                                                daysRemaining,
                                                                                                daysRemaining == 1
                                                                                                                ? messageSource.getMessage(
                                                                                                                                "common.day",
                                                                                                                                null,
                                                                                                                                locale)
                                                                                                                : messageSource.getMessage(
                                                                                                                                "common.days",
                                                                                                                                null,
                                                                                                                                locale)
                                                                                },
                                                                                locale);
                                                        case WHATSAPP:
                                                                return messageSource.getMessage(
                                                                                "whatsapp.extra.logins.expiration",
                                                                                new Object[] { userName, extraLogins
                                                                                                .getLoginCount(),
                                                                                                daysRemaining },
                                                                                locale);
                                                        case TELEGRAM:
                                                                return messageSource.getMessage(
                                                                                "telegram.extra.logins.expiration",
                                                                                new Object[] { userName, extraLogins
                                                                                                .getLoginCount(),
                                                                                                daysRemaining },
                                                                                locale);

                                                        case FCM:
                                                                Map<String, Object> fcmData = new HashMap<>();
                                                                fcmData.put("title", messageSource.getMessage(
                                                                                "fcm.extra.logins.expiration.title",
                                                                                null,
                                                                                locale));
                                                                fcmData.put("body", messageSource.getMessage(
                                                                                "fcm.extra.logins.expiration.body",
                                                                                new Object[] { extraLogins
                                                                                                .getLoginCount(),
                                                                                                daysRemaining },
                                                                                locale));
                                                                fcmData.put("type", "extra_logins_expiration");
                                                                fcmData.put("click_action",
                                                                                "FLUTTER_NOTIFICATION_CLICK"); // Required
                                                                                                               // for
                                                                                                               // Flutter
                                                                fcmData.put("custom_action", "OPEN_ACCOUNT_SETTINGS"); // Your
                                                                                                                       // custom
                                                                                                                       // action
                                                                fcmData.put("data", Map.of( // Group additional data
                                                                                "loginCount",
                                                                                extraLogins.getLoginCount(),
                                                                                "daysRemaining", daysRemaining,
                                                                                "expiryDate",
                                                                                extraLogins.getExpiryDate().format(
                                                                                                DateTimeFormatter.ISO_DATE_TIME)));
                                                                try {
                                                                        return new ObjectMapper()
                                                                                        .writeValueAsString(fcmData);
                                                                } catch (JsonProcessingException e) {
                                                                        log.error("Error serializing FCM extra logins expiration message",
                                                                                        e);
                                                                        return null;
                                                                }

                                                        case EMAIL:
                                                        default:
                                                                emailService.sendTemplatedEmail(
                                                                                user.getEmail(),
                                                                                "extra-logins-expiration",
                                                                                variables,
                                                                                locale);
                                                                return null;
                                                }
                                        });
                        log.debug("Sent extra logins expiration reminder to user: {}", user.getEmail());
                } catch (Exception e) {
                        log.error("Failed to send extra logins expiration reminder to user: {}",
                                        user.getEmail(), e);
                }
        }

        // Send Extra Logins Expired Notification
        @Override
        public void sendExtraLoginsExpiredNotification(User user, UserExtraLogins extraLogins) {
                try {
                        Locale locale = localeResolverService.resolveUserLocale(user);
                        String userName = getUserDisplayName(user);
                        String planName = (extraLogins.getPlan() != null) ? extraLogins.getPlan().getName()
                                        : "Default Plan";
                        int loginCount = extraLogins.getLoginCount();

                        Map<String, Object> variables = new HashMap<>();
                        variables.put("userName", userName);
                        variables.put("planName", planName);
                        variables.put("loginCount", loginCount);

                        multiChannelService.sendNotification(
                                        user,
                                        "extra-logins-expired",
                                        NotificationCategory.ACCOUNT,
                                        variables,
                                        message -> {
                                                switch (message.getChannel()) {
                                                        case SMS:
                                                                return messageSource.getMessage(
                                                                                "sms.extra.logins.expired",
                                                                                new Object[] { planName, loginCount },
                                                                                locale);
                                                        case WHATSAPP:
                                                                return messageSource.getMessage(
                                                                                "whatsapp.extra.logins.expired",
                                                                                new Object[] { userName, planName,
                                                                                                loginCount },
                                                                                locale);
                                                        case TELEGRAM:
                                                                return messageSource.getMessage(
                                                                                "telegram.extra.logins.expired",
                                                                                new Object[] { userName, planName,
                                                                                                loginCount },
                                                                                locale);

                                                        case FCM:
                                                                Map<String, Object> fcmData = new HashMap<>();
                                                                fcmData.put("title", messageSource.getMessage(
                                                                                "fcm.extra.logins.expired.title",
                                                                                null,
                                                                                locale));
                                                                fcmData.put("body", messageSource.getMessage(
                                                                                "fcm.extra.logins.expired.body",
                                                                                new Object[] { planName, loginCount },
                                                                                locale));
                                                                fcmData.put("type", "extra_logins_expired");
                                                                fcmData.put("click_action",
                                                                                "FLUTTER_NOTIFICATION_CLICK"); // Required
                                                                                                               // for
                                                                                                               // Flutter
                                                                fcmData.put("custom_action", "OPEN_ACCOUNT_SETTINGS"); // Your
                                                                                                                       // custom
                                                                                                                       // action
                                                                fcmData.put("data", Map.of( // Group additional data
                                                                                "planName", planName,
                                                                                "loginCount", loginCount));
                                                                try {
                                                                        return new ObjectMapper()
                                                                                        .writeValueAsString(fcmData);
                                                                } catch (JsonProcessingException e) {
                                                                        log.error("Error serializing FCM extra logins expired message",
                                                                                        e);
                                                                        return null;
                                                                }

                                                        case EMAIL:
                                                        default:
                                                                emailService.sendTemplatedEmail(
                                                                                user.getEmail(),
                                                                                "extra-logins-expired",
                                                                                variables,
                                                                                locale);
                                                                return null;
                                                }
                                        });
                        log.debug("Sent extra logins expired notification to user: {}", user.getEmail());
                } catch (Exception e) {
                        log.error("Failed to send extra logins expired notification to user: {}",
                                        user.getEmail(), e);
                }
        }

        // Send Extra Logins Gift Notification
        @Override
        public void sendExtraLoginsGiftNotification(User sender, User recipient, UserExtraLogins gift) {
                try {
                        // Notification for recipient
                        Locale recipientLocale = localeResolverService.resolveUserLocale(recipient);
                        String recipientName = getUserDisplayName(recipient);

                        Map<String, Object> recipientVariables = new HashMap<>();
                        recipientVariables.put("userName", recipientName);
                        recipientVariables.put("senderEmail", sender.getEmail());
                        recipientVariables.put("extraLogins", gift);

                        multiChannelService.sendNotification(
                                        recipient,
                                        "extra-logins-gift-received",
                                        NotificationCategory.ACCOUNT,
                                        recipientVariables,
                                        message -> {
                                                String expiry = gift.getExpiryDate() != null
                                                                ? messageSource.getMessage("until", null,
                                                                                recipientLocale) + " "
                                                                                + gift.getExpiryDate()
                                                                : messageSource.getMessage("permanently", null,
                                                                                recipientLocale);

                                                switch (message.getChannel()) {
                                                        case SMS:
                                                                return messageSource.getMessage(
                                                                                "sms.extra.logins.gift.received",
                                                                                new Object[] { gift.getLoginCount(),
                                                                                                expiry },
                                                                                recipientLocale);
                                                        case WHATSAPP:
                                                                return messageSource.getMessage(
                                                                                "whatsapp.extra.logins.gift.received",
                                                                                new Object[] { recipientName,
                                                                                                gift.getLoginCount(),
                                                                                                expiry },
                                                                                recipientLocale);
                                                        case TELEGRAM:
                                                                return messageSource.getMessage(
                                                                                "telegram.extra.logins.gift.received",
                                                                                new Object[] { recipientName,
                                                                                                gift.getLoginCount(),
                                                                                                expiry },
                                                                                recipientLocale);

                                                        case FCM:
                                                                Map<String, Object> fcmData = new HashMap<>();
                                                                fcmData.put("title", messageSource.getMessage(
                                                                                "fcm.extra.logins.gift.received.title",
                                                                                null,
                                                                                recipientLocale));
                                                                fcmData.put("body", messageSource.getMessage(
                                                                                "fcm.extra.logins.gift.received.body",
                                                                                new Object[] { gift.getLoginCount(),
                                                                                                expiry },
                                                                                recipientLocale));
                                                                fcmData.put("type", "extra_logins_gift_received");
                                                                fcmData.put("click_action",
                                                                                "FLUTTER_NOTIFICATION_CLICK"); // Required
                                                                                                               // for
                                                                                                               // Flutter
                                                                fcmData.put("custom_action", "OPEN_EXTRA_LOGINS"); // Your
                                                                                                                   // custom
                                                                                                                   // action
                                                                fcmData.put("data", Map.of( // Group additional data
                                                                                "senderEmail", sender.getEmail(),
                                                                                "loginCount", gift.getLoginCount(),
                                                                                "expiry", expiry));
                                                                try {
                                                                        return new ObjectMapper()
                                                                                        .writeValueAsString(fcmData);
                                                                } catch (JsonProcessingException e) {
                                                                        log.error("Error serializing FCM extra logins gift received message",
                                                                                        e);
                                                                        return null;
                                                                }

                                                        case EMAIL:
                                                        default:
                                                                emailService.sendTemplatedEmail(
                                                                                recipient.getEmail(),
                                                                                "extra-logins-gift-received",
                                                                                recipientVariables,
                                                                                recipientLocale);
                                                                return null;
                                                }
                                        });

                        // Notification for sender
                        Locale senderLocale = localeResolverService.resolveUserLocale(sender);
                        String senderName = getUserDisplayName(sender);

                        Map<String, Object> senderVariables = new HashMap<>();
                        senderVariables.put("userName", senderName);
                        senderVariables.put("recipientEmail", recipient.getEmail());
                        senderVariables.put("extraLogins", gift);

                        multiChannelService.sendNotification(
                                        sender,
                                        "extra-logins-gift-sent",
                                        NotificationCategory.ACCOUNT,
                                        senderVariables,
                                        message -> {
                                                switch (message.getChannel()) {
                                                        case SMS:
                                                                return messageSource.getMessage(
                                                                                "sms.extra.logins.gift.sent",
                                                                                new Object[] { recipient.getEmail(),
                                                                                                gift.getLoginCount() },
                                                                                senderLocale);
                                                        case WHATSAPP:
                                                                return messageSource.getMessage(
                                                                                "whatsapp.extra.logins.gift.sent",
                                                                                new Object[] { senderName,
                                                                                                recipient.getEmail(),
                                                                                                gift.getLoginCount() },
                                                                                senderLocale);
                                                        case TELEGRAM:
                                                                return messageSource.getMessage(
                                                                                "telegram.extra.logins.gift.sent",
                                                                                new Object[] { senderName,
                                                                                                recipient.getEmail(),
                                                                                                gift.getLoginCount() },
                                                                                senderLocale);

                                                        case FCM:
                                                                Map<String, Object> fcmData = new HashMap<>();
                                                                fcmData.put("title", messageSource.getMessage(
                                                                                "fcm.extra.logins.gift.sent.title",
                                                                                null,
                                                                                senderLocale));
                                                                fcmData.put("body", messageSource.getMessage(
                                                                                "fcm.extra.logins.gift.sent.body",
                                                                                new Object[] { recipient.getEmail(),
                                                                                                gift.getLoginCount() },
                                                                                senderLocale));
                                                                fcmData.put("type", "extra_logins_gift_sent");
                                                                fcmData.put("click_action",
                                                                                "FLUTTER_NOTIFICATION_CLICK"); // Required
                                                                                                               // for
                                                                                                               // Flutter
                                                                fcmData.put("custom_action", "OPEN_GIFT_HISTORY"); // Your
                                                                                                                   // custom
                                                                                                                   // action
                                                                fcmData.put("data", Map.of( // Group additional data
                                                                                "recipientEmail", recipient.getEmail(),
                                                                                "loginCount", gift.getLoginCount()));
                                                                try {
                                                                        return new ObjectMapper()
                                                                                        .writeValueAsString(fcmData);
                                                                } catch (JsonProcessingException e) {
                                                                        log.error("Error serializing FCM extra logins gift sent message",
                                                                                        e);
                                                                        return null;
                                                                }

                                                        case EMAIL:
                                                        default:
                                                                emailService.sendTemplatedEmail(
                                                                                sender.getEmail(),
                                                                                "extra-logins-gift-sent",
                                                                                senderVariables,
                                                                                senderLocale);
                                                                return null;
                                                }
                                        });

                        log.debug("Sent extra logins gift notifications - from: {} to: {}",
                                        sender.getEmail(), recipient.getEmail());
                } catch (Exception e) {
                        log.error("Failed to send extra logins gift notifications", e);
                }
        }

        // Send Gift Card Cancellation Notification
        @Override
        public void sendGiftCardCancellationNotification(GiftCard giftCard) {
                User cardOwner = giftCard.getRedeemedBy();
                if (cardOwner == null) {
                        log.warn("No user associated with cancelled gift card: {}", giftCard.getCode());
                        return;
                }

                try {
                        Locale locale = localeResolverService.resolveUserLocale(cardOwner);
                        String userName = getUserDisplayName(cardOwner);

                        Map<String, Object> variables = new HashMap<>();
                        variables.put("userName", userName);
                        variables.put("giftCard", giftCard);

                        multiChannelService.sendNotification(
                                        cardOwner,
                                        "gift-card-cancellation",
                                        NotificationCategory.BILLING,
                                        variables,
                                        message -> {
                                                switch (message.getChannel()) {
                                                        case SMS:
                                                                return messageSource.getMessage(
                                                                                "sms.giftcard.cancellation",
                                                                                new Object[] { giftCard.getCode() },
                                                                                locale);
                                                        case WHATSAPP:
                                                                return messageSource.getMessage(
                                                                                "whatsapp.giftcard.cancellation",
                                                                                new Object[] { userName,
                                                                                                giftCard.getCode() },
                                                                                locale);
                                                        case TELEGRAM:
                                                                return messageSource.getMessage(
                                                                                "telegram.giftcard.cancellation",
                                                                                new Object[] { userName,
                                                                                                giftCard.getCode() },
                                                                                locale);

                                                        case FCM:
                                                                Map<String, Object> fcmData = new HashMap<>();
                                                                fcmData.put("title", messageSource.getMessage(
                                                                                "fcm.giftcard.cancellation.title",
                                                                                null,
                                                                                locale));
                                                                fcmData.put("body", messageSource.getMessage(
                                                                                "fcm.giftcard.cancellation.body",
                                                                                new Object[] { giftCard.getCode() },
                                                                                locale));
                                                                fcmData.put("type", "gift_card_cancellation");
                                                                fcmData.put("click_action",
                                                                                "FLUTTER_NOTIFICATION_CLICK"); // Required
                                                                                                               // for
                                                                                                               // Flutter
                                                                fcmData.put("custom_action", "OPEN_GIFT_CARD_DETAILS"); // Your
                                                                                                                        // custom
                                                                                                                        // action
                                                                fcmData.put("data", Map.of( // Group additional data
                                                                                "giftCardCode", giftCard.getCode()));
                                                                try {
                                                                        return new ObjectMapper()
                                                                                        .writeValueAsString(fcmData);
                                                                } catch (JsonProcessingException e) {
                                                                        log.error("Error serializing FCM gift card cancellation message",
                                                                                        e);
                                                                        return null;
                                                                }

                                                        case EMAIL:
                                                        default:
                                                                emailService.sendTemplatedEmail(
                                                                                cardOwner.getEmail(),
                                                                                "gift-card-cancellation",
                                                                                variables,
                                                                                locale);
                                                                return null;
                                                }
                                        });
                        log.debug("Sent gift card cancellation notification for code: {} to user: {}",
                                        giftCard.getCode(), cardOwner.getEmail());
                } catch (Exception e) {
                        log.error("Failed to send gift card cancellation notification", e);
                }
        }

        // Send User Password Re-Encryption
        @Override
        public void notifyUserPasswordReEncryption(User user, String newPassword) {
                try {
                        Locale locale = localeResolverService.resolveUserLocale(user);
                        String userName = getUserDisplayName(user);

                        Map<String, Object> variables = new HashMap<>();
                        variables.put("userName", userName);
                        variables.put("newPassword", newPassword);

                        multiChannelService.sendNotification(
                                        user,
                                        "password-reencryption",
                                        NotificationCategory.SECURITY,
                                        variables,
                                        message -> {
                                                switch (message.getChannel()) {
                                                        case SMS:
                                                                return messageSource.getMessage(
                                                                                "sms.password.reencryption",
                                                                                new Object[] { newPassword },
                                                                                locale);
                                                        case WHATSAPP:
                                                                return messageSource.getMessage(
                                                                                "whatsapp.password.reencryption",
                                                                                new Object[] { userName, newPassword },
                                                                                locale);
                                                        case TELEGRAM:
                                                                return messageSource.getMessage(
                                                                                "telegram.password.reencryption",
                                                                                new Object[] { userName, newPassword },
                                                                                locale);

                                                        case FCM:
                                                                Map<String, Object> fcmData = new HashMap<>();
                                                                fcmData.put("title", messageSource.getMessage(
                                                                                "fcm.password.reencryption.title",
                                                                                null,
                                                                                locale));
                                                                fcmData.put("body", messageSource.getMessage(
                                                                                "fcm.password.reencryption.body",
                                                                                null,
                                                                                locale));
                                                                fcmData.put("type", "password_reencryption");
                                                                fcmData.put("click_action",
                                                                                "FLUTTER_NOTIFICATION_CLICK"); // Required
                                                                                                               // for
                                                                                                               // Flutter
                                                                fcmData.put("custom_action", "OPEN_LOGIN"); // Action to
                                                                                                            // open
                                                                                                            // login
                                                                                                            // screen
                                                                fcmData.put("data", Map.of( // Group additional data
                                                                                "userName", userName,
                                                                                "newPassword", newPassword));
                                                                try {
                                                                        return new ObjectMapper()
                                                                                        .writeValueAsString(fcmData);
                                                                } catch (JsonProcessingException e) {
                                                                        log.error("Error serializing FCM password reencryption message",
                                                                                        e);
                                                                        return null;
                                                                }

                                                        case EMAIL:
                                                        default:
                                                                emailService.sendTemplatedEmail(
                                                                                user.getEmail(),
                                                                                "password-reencryption",
                                                                                variables,
                                                                                locale);
                                                                return null;
                                                }
                                        });
                        log.debug("Sent password re-encryption notification to user: {}", user.getEmail());
                } catch (Exception e) {
                        log.error("Failed to send password re-encryption notification to user: {}",
                                        user.getEmail(), e);
                }
        }

        // Welcoming New Users Without Subscription
        @Override
        public void welcomingNewUsersWithoutSubscription(User user, String password) {
                try {
                        Locale locale = localeResolverService.resolveUserLocale(user);
                        String userName = getUserDisplayName(user);

                        Map<String, Object> variables = new HashMap<>();
                        variables.put("userName", userName);
                        variables.put("username", user.getUsername());
                        variables.put("password", password);

                        multiChannelService.sendNotification(
                                        user,
                                        "welcome-new-user-no-subscription",
                                        NotificationCategory.ACCOUNT,
                                        variables,
                                        message -> {
                                                switch (message.getChannel()) {
                                                        case SMS:
                                                                return messageSource.getMessage(
                                                                                "sms.welcome.new.user.no.subscription",
                                                                                new Object[] { user.getUsername(),
                                                                                                password },
                                                                                locale);
                                                        case WHATSAPP:
                                                                return messageSource.getMessage(
                                                                                "whatsapp.welcome.new.user.no.subscription",
                                                                                new Object[] { userName,
                                                                                                user.getUsername(),
                                                                                                password },
                                                                                locale);
                                                        case TELEGRAM:
                                                                return messageSource.getMessage(
                                                                                "telegram.welcome.new.user.no.subscription",
                                                                                new Object[] { userName,
                                                                                                user.getUsername(),
                                                                                                password },
                                                                                locale);
                                                        case FCM:
                                                                Map<String, Object> fcmData = new HashMap<>();
                                                                fcmData.put("title", messageSource.getMessage(
                                                                                "fcm.welcome.new.user.no.subscription.title",
                                                                                null,
                                                                                locale));
                                                                fcmData.put("body", messageSource.getMessage(
                                                                                "fcm.welcome.new.user.no.subscription.body",
                                                                                new Object[] { userName },
                                                                                locale));
                                                                fcmData.put("type", "welcome_new_user_no_subscription");
                                                                fcmData.put("click_action",
                                                                                "FLUTTER_NOTIFICATION_CLICK"); // Required
                                                                                                               // for
                                                                                                               // Flutter
                                                                fcmData.put("custom_action", "OPEN_SUBSCRIPTION_PAGE"); // Your
                                                                                                                        // custom
                                                                                                                        // action
                                                                fcmData.put("data", Map.of( // Group additional data
                                                                                "username", user.getUsername(),
                                                                                "password", password,
                                                                                "userName", userName));
                                                                try {
                                                                        return new ObjectMapper()
                                                                                        .writeValueAsString(fcmData);
                                                                } catch (JsonProcessingException e) {
                                                                        log.error("Error serializing FCM welcome new user message",
                                                                                        e);
                                                                        return null;
                                                                }

                                                        case EMAIL:
                                                        default:
                                                                emailService.sendTemplatedEmail(
                                                                                user.getEmail(),
                                                                                "welcome-new-user-no-subscription",
                                                                                variables,
                                                                                locale);
                                                                return null;
                                                }
                                        });
                        log.debug("Sent welcome notifications to new user without subscription: {}", user.getEmail());
                } catch (Exception e) {
                        log.error("Failed to send welcome notifications to user: {}", user.getEmail(), e);
                }
        }

        // Send Verification Email
        @Override
        public void sendVerificationEmail(String toEmail, String verificationCode, Locale locale) {
                Map<String, Object> variables = new HashMap<>();
                variables.put("token", verificationCode);

                multiChannelService.sendNotification(
                                toEmail,
                                "email-verification",
                                NotificationCategory.ACCOUNT,
                                variables,
                                message -> {
                                        switch (message.getChannel()) {
                                                case SMS:
                                                        return messageSource.getMessage(
                                                                        "sms.email.verification",
                                                                        new Object[] { verificationCode },
                                                                        locale);
                                                case WHATSAPP:
                                                        return messageSource.getMessage(
                                                                        "whatsapp.email.verification",
                                                                        new Object[] { verificationCode },
                                                                        locale);
                                                case TELEGRAM:
                                                        return messageSource.getMessage(
                                                                        "telegram.email.verification",
                                                                        new Object[] { verificationCode },
                                                                        locale);
                                                case EMAIL:
                                                default:
                                                        emailService.sendTemplatedEmail(
                                                                        toEmail,
                                                                        "email-verification",
                                                                        variables,
                                                                        locale);
                                                        return null;
                                        }
                                });
        }

        // Send Success Verification Email
        @Override
        public void sendSuccessVerificationEmail(String toEmail, Locale locale) {
                Map<String, Object> variables = new HashMap<>();

                multiChannelService.sendNotification(
                                toEmail,
                                "email-verification-success",
                                NotificationCategory.ACCOUNT,
                                variables,
                                message -> {
                                        switch (message.getChannel()) {
                                                case SMS:
                                                        return messageSource.getMessage(
                                                                        "sms.email.verification.success",
                                                                        null,
                                                                        locale);
                                                case WHATSAPP:
                                                        return messageSource.getMessage(
                                                                        "whatsapp.email.verification.success",
                                                                        null,
                                                                        locale);
                                                case TELEGRAM:
                                                        return messageSource.getMessage(
                                                                        "telegram.email.verification.success",
                                                                        null,
                                                                        locale);

                                                case FCM:
                                                        Map<String, Object> fcmData = new HashMap<>();
                                                        fcmData.put("title", messageSource.getMessage(
                                                                        "fcm.email.verification.success.title",
                                                                        null,
                                                                        locale));
                                                        fcmData.put("body", messageSource.getMessage(
                                                                        "fcm.email.verification.success.body",
                                                                        null,
                                                                        locale));
                                                        fcmData.put("type", "email_verification_success");
                                                        fcmData.put("click_action", "FLUTTER_NOTIFICATION_CLICK"); // Required
                                                                                                                   // for
                                                                                                                   // Flutter
                                                        fcmData.put("custom_action", "OPEN_EMAIL_VERIFICATION_SUCCESS"); // Your
                                                                                                                         // custom
                                                                                                                         // action
                                                        try {
                                                                return new ObjectMapper()
                                                                                .writeValueAsString(fcmData);
                                                        } catch (JsonProcessingException e) {
                                                                log.error("Error serializing FCM email verification success message",
                                                                                e);
                                                                return null;
                                                        }

                                                case EMAIL:
                                                default:
                                                        emailService.sendTemplatedEmail(
                                                                        toEmail,
                                                                        "email-verification-success",
                                                                        variables,
                                                                        locale);
                                                        return null;
                                        }
                                });
        }

        // Send System Notification
        @Override
        public void sendSystemNotification(String toEmail, Map<String, Object> variables, Locale locale) {
                multiChannelService.sendNotification(
                                toEmail,
                                "system-notification",
                                NotificationCategory.SYSTEM,
                                variables,
                                message -> {
                                        String notificationMessage = (String) variables.get("message");
                                        switch (message.getChannel()) {
                                                case SMS:
                                                        return messageSource.getMessage(
                                                                        "sms.system.notification",
                                                                        new Object[] { notificationMessage },
                                                                        locale);
                                                case WHATSAPP:
                                                        return messageSource.getMessage(
                                                                        "whatsapp.system.notification",
                                                                        new Object[] { notificationMessage },
                                                                        locale);
                                                case TELEGRAM:
                                                        return messageSource.getMessage(
                                                                        "telegram.system.notification",
                                                                        new Object[] { notificationMessage },
                                                                        locale);

                                                case FCM:
                                                        Map<String, Object> fcmData = new HashMap<>();
                                                        fcmData.put("title", messageSource.getMessage(
                                                                        "fcm.system.notification.title",
                                                                        null,
                                                                        locale));
                                                        fcmData.put("body", messageSource.getMessage(
                                                                        "fcm.system.notification.body",
                                                                        new Object[] { notificationMessage },
                                                                        locale));
                                                        fcmData.put("type", "system_notification");
                                                        fcmData.put("click_action", "FLUTTER_NOTIFICATION_CLICK"); // Required
                                                                                                                   // for
                                                                                                                   // Flutter
                                                        fcmData.put("custom_action", "OPEN_SYSTEM_NOTIFICATION"); // Your
                                                                                                                  // custom
                                                                                                                  // action
                                                        fcmData.put("data", Map.of( // Group additional data
                                                                        "message", notificationMessage));
                                                        try {
                                                                return new ObjectMapper()
                                                                                .writeValueAsString(fcmData);
                                                        } catch (JsonProcessingException e) {
                                                                log.error("Error serializing FCM system notification message",
                                                                                e);
                                                                return null;
                                                        }

                                                case EMAIL:
                                                default:
                                                        emailService.sendTemplatedEmail(
                                                                        toEmail,
                                                                        "system-notification",
                                                                        variables,
                                                                        locale);
                                                        return null;
                                        }
                                });
        }

        @Override
        public void sendSubscriptionRenewalNotification(User user, UserSubscription subscription) {
                try {
                        Locale locale = localeResolverService.resolveUserLocale(user);
                        String userName = getUserDisplayName(user);

                        // Create variables map with exact template variable names
                        Map<String, Object> variables = new HashMap<>();
                        variables.put("userName", userName);
                        variables.put("username", user.getUsername());
                        variables.put("subscription", subscription);
                        variables.put("duration", subscription.getGroup().getDuration());
                        variables.put("devices", subscription.getMultiLoginCount());
                        variables.put("formattedExpiry", subscription.getExpiresAt().format(
                                        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")));

                        multiChannelService.sendNotification(
                                        user,
                                        "subscription-renewal",
                                        NotificationCategory.BILLING,
                                        variables,
                                        message -> {
                                                switch (message.getChannel()) {
                                                        case SMS:
                                                                return messageSource.getMessage(
                                                                                "sms.subscription.renewal",
                                                                                new Object[] {
                                                                                                subscription.getMultiLoginCount(),
                                                                                                subscription.getDuration(),
                                                                                                subscription.getExpiresAt()
                                                                                                                .format(DateTimeFormatter
                                                                                                                                .ofPattern("dd-MM-yyyy"))
                                                                                },
                                                                                locale);

                                                        case WHATSAPP:
                                                                return messageSource.getMessage(
                                                                                "whatsapp.subscription.renewal",
                                                                                new Object[] {
                                                                                                userName,
                                                                                                subscription.getGroup()
                                                                                                                .getName(),
                                                                                                subscription.getDuration(),
                                                                                                subscription.getMultiLoginCount()
                                                                                },
                                                                                locale);

                                                        case TELEGRAM:
                                                                return messageSource.getMessage(
                                                                                "telegram.subscription.renewal",
                                                                                new Object[] {
                                                                                                userName,
                                                                                                subscription.getGroup()
                                                                                                                .getName(),
                                                                                                subscription.getDuration(),
                                                                                                subscription.getMultiLoginCount()
                                                                                },
                                                                                locale);

                                                        case FCM:
                                                                Map<String, Object> fcmData = new HashMap<>();
                                                                fcmData.put("title", messageSource.getMessage(
                                                                                "fcm.subscription.renewal.title",
                                                                                null,
                                                                                locale));
                                                                fcmData.put("body", messageSource.getMessage(
                                                                                "fcm.subscription.renewal.body",
                                                                                new Object[] { subscription
                                                                                                .getDuration() },
                                                                                locale));
                                                                fcmData.put("type", "subscription_renewed");
                                                                fcmData.put("click_action",
                                                                                "FLUTTER_NOTIFICATION_CLICK");
                                                                fcmData.put("custom_action", "OPEN_SUBSCRIPTION");
                                                                fcmData.put("data", Map.of(
                                                                                "plan",
                                                                                subscription.getGroup().getName(),
                                                                                "duration", subscription.getDuration(),
                                                                                "devices",
                                                                                subscription.getMultiLoginCount(),
                                                                                "expiryDate",
                                                                                subscription.getExpiresAt().format(
                                                                                                DateTimeFormatter.ISO_DATE_TIME)));
                                                                try {
                                                                        return objectMapper.writeValueAsString(fcmData);
                                                                } catch (JsonProcessingException e) {
                                                                        log.error("Error serializing FCM renewal notification",
                                                                                        e);
                                                                        return null;
                                                                }

                                                        case EMAIL:
                                                        default:
                                                                emailService.sendTemplatedEmail(
                                                                                user.getEmail(),
                                                                                "subscription-renewal-confirmation",
                                                                                variables,
                                                                                locale);
                                                                return null;
                                                }
                                        });
                        log.debug("Sent subscription renewal notification to user: {}", user.getEmail());
                } catch (Exception e) {
                        log.error("Failed to send subscription renewal notification to user: {}", user.getEmail(), e);
                }
        }

        // Send Invoice Email
        @Override
        public void sendInvoiceEmail(String toEmail, Map<String, Object> variables, Locale locale) {
                multiChannelService.sendNotification(
                                toEmail,
                                "invoice-email",
                                NotificationCategory.BILLING,
                                variables,
                                message -> {
                                        switch (message.getChannel()) {
                                                case SMS:
                                                        return messageSource.getMessage(
                                                                        "sms.invoice.notification",
                                                                        new Object[] { variables.get("invoiceNumber") },
                                                                        locale);
                                                case WHATSAPP:
                                                        return messageSource.getMessage(
                                                                        "whatsapp.invoice.notification",
                                                                        new Object[] { variables.get("invoiceNumber") },
                                                                        locale);
                                                case TELEGRAM:
                                                        return messageSource.getMessage(
                                                                        "telegram.invoice.notification",
                                                                        new Object[] { variables.get("invoiceNumber") },
                                                                        locale);

                                                case FCM:
                                                        Map<String, Object> fcmData = new HashMap<>();
                                                        fcmData.put("title", messageSource.getMessage(
                                                                        "fcm.invoice.notification.title",
                                                                        null,
                                                                        locale));
                                                        fcmData.put("body", messageSource.getMessage(
                                                                        "fcm.invoice.notification.body",
                                                                        new Object[] { variables.get("invoiceNumber") },
                                                                        locale));
                                                        fcmData.put("type", "invoice_notification");
                                                        fcmData.put("click_action", "FLUTTER_NOTIFICATION_CLICK"); // Required
                                                                                                                   // for
                                                                                                                   // Flutter
                                                        fcmData.put("custom_action", "OPEN_INVOICE"); // Your custom
                                                                                                      // action
                                                        fcmData.put("data", Map.of( // Group additional data
                                                                        "invoiceNumber",
                                                                        variables.get("invoiceNumber")));
                                                        try {
                                                                return new ObjectMapper()
                                                                                .writeValueAsString(fcmData);
                                                        } catch (JsonProcessingException e) {
                                                                log.error("Error serializing FCM invoice notification message",
                                                                                e);
                                                                return null;
                                                        }

                                                case EMAIL:
                                                default:
                                                        emailService.sendTemplatedEmail(
                                                                        toEmail,
                                                                        "invoice-email",
                                                                        variables,
                                                                        locale);
                                                        return null;
                                        }
                                });
        }

        // Send Token Code To User
        @Override
        public void sendTokenCodeToUser(User user, String token) {
                try {
                        Locale locale = localeResolverService.resolveUserLocale(user);
                        String userName = getUserDisplayName(user);

                        Map<String, Object> variables = new HashMap<>();
                        variables.put("userName", userName);
                        variables.put("token", token);

                        multiChannelService.sendNotification(
                                        user,
                                        "token-code",
                                        NotificationCategory.BILLING,
                                        variables,
                                        message -> {
                                                switch (message.getChannel()) {
                                                        case SMS:
                                                                return messageSource.getMessage(
                                                                                "sms.token.code",
                                                                                new Object[] { token },
                                                                                locale);
                                                        case WHATSAPP:
                                                                return messageSource.getMessage(
                                                                                "whatsapp.token.code",
                                                                                new Object[] { userName, token },
                                                                                locale);
                                                        case TELEGRAM:
                                                                return messageSource.getMessage(
                                                                                "telegram.token.code",
                                                                                new Object[] { userName, token },
                                                                                locale);

                                                        case FCM:
                                                                Map<String, Object> fcmData = new HashMap<>();
                                                                fcmData.put("title", messageSource.getMessage(
                                                                                "fcm.token.code.title",
                                                                                null,
                                                                                locale));
                                                                fcmData.put("body", messageSource.getMessage(
                                                                                "fcm.token.code.body",
                                                                                new Object[] { token },
                                                                                locale));
                                                                fcmData.put("type", "token_code");
                                                                fcmData.put("click_action",
                                                                                "FLUTTER_NOTIFICATION_CLICK"); // Required
                                                                                                               // for
                                                                                                               // Flutter
                                                                fcmData.put("custom_action", "OPEN_TOKEN_CODE"); // Your
                                                                                                                 // custom
                                                                                                                 // action
                                                                fcmData.put("data", Map.of( // Group additional data
                                                                                "token", token));
                                                                try {
                                                                        return new ObjectMapper()
                                                                                        .writeValueAsString(fcmData);
                                                                } catch (JsonProcessingException e) {
                                                                        log.error("Error serializing FCM token code message",
                                                                                        e);
                                                                        return null;
                                                                }

                                                        case EMAIL:
                                                        default:
                                                                emailService.sendTemplatedEmail(
                                                                                user.getEmail(),
                                                                                "token-code",
                                                                                variables,
                                                                                locale);
                                                                return null;
                                                }
                                        });
                        log.debug("Sent token code notification to user: {}", user.getEmail());
                } catch (Exception e) {
                        log.error("Failed to send token code notification to user: {}", user.getEmail(), e);
                }
        }

        // Send Magic Login Code
        @Override
        public void sendMagicLoginCode(User user, String code) {
                try {
                        Locale locale = localeResolverService.resolveUserLocale(user);
                        String userName = getUserDisplayName(user);

                        Map<String, Object> variables = new HashMap<>();
                        variables.put("userName", userName);
                        variables.put("code", code);

                        multiChannelService.sendNotification(
                                        user,
                                        "magic-login",
                                        NotificationCategory.SECURITY,
                                        variables,
                                        message -> {
                                                switch (message.getChannel()) {
                                                        case SMS:
                                                                return messageSource.getMessage(
                                                                                "sms.magic.login",
                                                                                new Object[] { code },
                                                                                locale);
                                                        case WHATSAPP:
                                                                return messageSource.getMessage(
                                                                                "whatsapp.magic.login",
                                                                                new Object[] { userName, code },
                                                                                locale);
                                                        case TELEGRAM:
                                                                return messageSource.getMessage(
                                                                                "telegram.magic.login",
                                                                                new Object[] { userName, code },
                                                                                locale);

                                                        case FCM:
                                                                Map<String, Object> fcmData = new HashMap<>();
                                                                fcmData.put("title", messageSource.getMessage(
                                                                                "fcm.magic.login.title",
                                                                                null,
                                                                                locale));
                                                                fcmData.put("body", messageSource.getMessage(
                                                                                "fcm.magic.login.body",
                                                                                null,
                                                                                locale));
                                                                fcmData.put("type", "magic_login_code");
                                                                fcmData.put("click_action",
                                                                                "FLUTTER_NOTIFICATION_CLICK");
                                                                fcmData.put("custom_action", "OPEN_MAGIC_LOGIN");
                                                                fcmData.put("data", Map.of(
                                                                                "code", code));
                                                                try {
                                                                        return new ObjectMapper()
                                                                                        .writeValueAsString(fcmData);
                                                                } catch (JsonProcessingException e) {
                                                                        log.error("Error serializing FCM magic login message",
                                                                                        e);
                                                                        return null;
                                                                }

                                                        case EMAIL:
                                                        default:
                                                                emailService.sendTemplatedEmail(
                                                                                user.getEmail(),
                                                                                "magic-login",
                                                                                variables,
                                                                                locale);
                                                                return null;
                                                }
                                        });
                        log.debug("Sent magic login code to user: {}", user.getEmail());
                } catch (Exception e) {
                        log.error("Failed to send magic login code to user: {}", user.getEmail(), e);
                }
        }

        // Send Magic Link (URL-based passwordless login)
        @Override
        public void sendMagicLink(User user, String token) {
                try {
                        Locale locale = localeResolverService.resolveUserLocale(user);
                        String userName = getUserDisplayName(user);

                        // Build the magic link URL
                        String magicLinkUrl = apiBaseUrl + "/auth/magic-link?token=" + token;

                        Map<String, Object> variables = new HashMap<>();
                        variables.put("userName", userName);
                        variables.put("magicLinkUrl", magicLinkUrl);

                        // For magic links, we only send via email (link is not suitable for SMS/WhatsApp)
                        emailService.sendTemplatedEmail(
                                        user.getEmail(),
                                        "magic-link",
                                        variables,
                                        locale);

                        log.debug("Sent magic link to user: {}", user.getEmail());
                } catch (Exception e) {
                        log.error("Failed to send magic link to user: {}", user.getEmail(), e);
                }
        }

        private String getUserDisplayName(User user) {
                String firstName = user.getProfile() != null ? user.getProfile().getFirstName() : null;
                return (firstName != null && !firstName.trim().isEmpty()) ? firstName : user.getEmail();
        }

        // Helper method for creating mailto links
        private String createMailtoLink(String subject, String body) {
                return "mailto:info@orbvpn.xyz?subject=" +
                                URLEncoder.encode(subject, StandardCharsets.UTF_8) +
                                "&body=" + URLEncoder.encode(body, StandardCharsets.UTF_8);
        }

        @Override
        public void sendTrialEndingNotification(User user, UserSubscription subscription, LocalDateTime trialEnd) {
                try {
                        Locale locale = localeResolverService.resolveUserLocale(user);
                        Map<String, Object> variables = new HashMap<>();
                        variables.put("userName", getUserDisplayName(user));
                        variables.put("trialEndDate", trialEnd != null ? trialEnd.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")) : "soon");
                        variables.put("planName", subscription.getGroup() != null ? subscription.getGroup().getName() : "your plan");

                        String subject = messageSource.getMessage("notification.trial.ending.subject",
                                        new Object[] {}, "Your trial is ending soon", locale);
                        String messageText = messageSource.getMessage("notification.trial.ending.message",
                                        new Object[] { getUserDisplayName(user), variables.get("trialEndDate") },
                                        "Your trial period is ending soon. Please update your payment method to continue enjoying our service.",
                                        locale);

                        variables.put("subject", subject);
                        variables.put("message", messageText);

                        multiChannelService.sendNotification(
                                        user,
                                        "trial-ending",
                                        NotificationCategory.BILLING,
                                        variables,
                                        message -> {
                                                switch (message.getChannel()) {
                                                        case EMAIL:
                                                                emailService.sendTemplatedEmail(
                                                                                user.getEmail(),
                                                                                "subscription-expiry-reminder",
                                                                                variables,
                                                                                locale);
                                                                return null;
                                                        default:
                                                                return null;
                                                }
                                        });
                        log.info("Sent trial ending notification to user: {}", user.getEmail());
                } catch (Exception e) {
                        log.error("Failed to send trial ending notification to user: {}", user.getEmail(), e);
                }
        }

        @Override
        public void sendPaymentFailedNotification(User user, UserSubscription subscription) {
                try {
                        Locale locale = localeResolverService.resolveUserLocale(user);
                        Map<String, Object> variables = new HashMap<>();
                        variables.put("userName", getUserDisplayName(user));
                        variables.put("planName", subscription.getGroup() != null ? subscription.getGroup().getName() : "your plan");

                        String subject = messageSource.getMessage("notification.payment.failed.subject",
                                        new Object[] {}, "Payment Failed - Action Required", locale);
                        String messageText = messageSource.getMessage("notification.payment.failed.message",
                                        new Object[] { getUserDisplayName(user) },
                                        "We were unable to process your payment. Please update your payment method to avoid service interruption.",
                                        locale);

                        variables.put("subject", subject);
                        variables.put("message", messageText);

                        multiChannelService.sendNotification(
                                        user,
                                        "payment-failed",
                                        NotificationCategory.BILLING,
                                        variables,
                                        message -> {
                                                switch (message.getChannel()) {
                                                        case EMAIL:
                                                                emailService.sendTemplatedEmail(
                                                                                user.getEmail(),
                                                                                "payment-failed",
                                                                                variables,
                                                                                locale);
                                                                return null;
                                                        default:
                                                                return null;
                                                }
                                        });
                        log.info("Sent payment failed notification to user: {}", user.getEmail());
                } catch (Exception e) {
                        log.error("Failed to send payment failed notification to user: {}", user.getEmail(), e);
                }
        }

        // ==================== NEW NOTIFICATIONS ====================

        /**
         * Send payment successful notification
         */
        public void sendPaymentSuccessNotification(User user, UserSubscription subscription, java.math.BigDecimal amount) {
                try {
                        Locale locale = localeResolverService.resolveUserLocale(user);
                        String userName = getUserDisplayName(user);

                        Map<String, Object> variables = new HashMap<>();
                        variables.put("userName", userName);
                        variables.put("amount", amount != null ? amount.toString() : "0.00");
                        variables.put("planName", subscription.getGroup() != null ? subscription.getGroup().getName() : "Subscription");
                        variables.put("duration", subscription.getDuration());
                        variables.put("devices", subscription.getMultiLoginCount());
                        variables.put("formattedExpiry", subscription.getExpiresAt().format(
                                        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")));

                        multiChannelService.sendNotification(
                                        user,
                                        "payment-success",
                                        NotificationCategory.BILLING,
                                        variables,
                                        message -> {
                                                switch (message.getChannel()) {
                                                        case SMS:
                                                                return messageSource.getMessage(
                                                                                "sms.payment.success",
                                                                                new Object[] { amount, subscription.getGroup().getName() },
                                                                                locale);
                                                        case WHATSAPP:
                                                                return messageSource.getMessage(
                                                                                "whatsapp.payment.success",
                                                                                new Object[] { userName, amount, subscription.getGroup().getName() },
                                                                                locale);
                                                        case TELEGRAM:
                                                                return messageSource.getMessage(
                                                                                "telegram.payment.success",
                                                                                new Object[] { userName, amount, subscription.getGroup().getName() },
                                                                                locale);
                                                        case FCM:
                                                                Map<String, Object> fcmData = new HashMap<>();
                                                                fcmData.put("title", messageSource.getMessage(
                                                                                "fcm.payment.success.title", null, locale));
                                                                fcmData.put("body", messageSource.getMessage(
                                                                                "fcm.payment.success.body",
                                                                                new Object[] { amount }, locale));
                                                                fcmData.put("type", "payment_success");
                                                                fcmData.put("click_action", "FLUTTER_NOTIFICATION_CLICK");
                                                                fcmData.put("custom_action", "OPEN_SUBSCRIPTION");
                                                                try {
                                                                        return objectMapper.writeValueAsString(fcmData);
                                                                } catch (JsonProcessingException e) {
                                                                        log.error("Error serializing FCM payment success notification", e);
                                                                        return null;
                                                                }
                                                        case EMAIL:
                                                        default:
                                                                emailService.sendTemplatedEmail(
                                                                                user.getEmail(),
                                                                                "payment-success",
                                                                                variables,
                                                                                locale);
                                                                return null;
                                                }
                                        });
                        log.info("Sent payment success notification to user: {}", user.getEmail());
                } catch (Exception e) {
                        log.error("Failed to send payment success notification to user: {}", user.getEmail(), e);
                }
        }

        /**
         * Send subscription auto-renewed notification
         */
        public void sendSubscriptionAutoRenewedNotification(User user, UserSubscription subscription, java.math.BigDecimal amountCharged) {
                try {
                        Locale locale = localeResolverService.resolveUserLocale(user);
                        String userName = getUserDisplayName(user);

                        Map<String, Object> variables = new HashMap<>();
                        variables.put("userName", userName);
                        variables.put("amount", amountCharged != null ? amountCharged.toString() : "0.00");
                        variables.put("planName", subscription.getGroup() != null ? subscription.getGroup().getName() : "Subscription");
                        variables.put("duration", subscription.getDuration());
                        variables.put("devices", subscription.getMultiLoginCount());
                        variables.put("formattedExpiry", subscription.getExpiresAt().format(
                                        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")));

                        multiChannelService.sendNotification(
                                        user,
                                        "subscription-auto-renewed",
                                        NotificationCategory.BILLING,
                                        variables,
                                        message -> {
                                                switch (message.getChannel()) {
                                                        case SMS:
                                                                return messageSource.getMessage(
                                                                                "sms.subscription.auto.renewed",
                                                                                new Object[] { amountCharged, subscription.getExpiresAt().format(
                                                                                                DateTimeFormatter.ofPattern("dd-MM-yyyy")) },
                                                                                locale);
                                                        case WHATSAPP:
                                                                return messageSource.getMessage(
                                                                                "whatsapp.subscription.auto.renewed",
                                                                                new Object[] { userName, amountCharged, subscription.getGroup().getName() },
                                                                                locale);
                                                        case TELEGRAM:
                                                                return messageSource.getMessage(
                                                                                "telegram.subscription.auto.renewed",
                                                                                new Object[] { userName, amountCharged, subscription.getGroup().getName() },
                                                                                locale);
                                                        case FCM:
                                                                Map<String, Object> fcmData = new HashMap<>();
                                                                fcmData.put("title", messageSource.getMessage(
                                                                                "fcm.subscription.auto.renewed.title", null, locale));
                                                                fcmData.put("body", messageSource.getMessage(
                                                                                "fcm.subscription.auto.renewed.body",
                                                                                new Object[] { amountCharged }, locale));
                                                                fcmData.put("type", "subscription_auto_renewed");
                                                                fcmData.put("click_action", "FLUTTER_NOTIFICATION_CLICK");
                                                                fcmData.put("custom_action", "OPEN_SUBSCRIPTION");
                                                                try {
                                                                        return objectMapper.writeValueAsString(fcmData);
                                                                } catch (JsonProcessingException e) {
                                                                        log.error("Error serializing FCM auto-renewed notification", e);
                                                                        return null;
                                                                }
                                                        case EMAIL:
                                                        default:
                                                                emailService.sendTemplatedEmail(
                                                                                user.getEmail(),
                                                                                "subscription-auto-renewed",
                                                                                variables,
                                                                                locale);
                                                                return null;
                                                }
                                        });
                        log.info("Sent subscription auto-renewed notification to user: {}", user.getEmail());
                } catch (Exception e) {
                        log.error("Failed to send subscription auto-renewed notification to user: {}", user.getEmail(), e);
                }
        }

        /**
         * Send subscription upgraded/downgraded notification
         */
        public void sendSubscriptionPlanChangedNotification(User user, UserSubscription subscription, String oldPlanName, String newPlanName) {
                try {
                        Locale locale = localeResolverService.resolveUserLocale(user);
                        String userName = getUserDisplayName(user);

                        Map<String, Object> variables = new HashMap<>();
                        variables.put("userName", userName);
                        variables.put("oldPlanName", oldPlanName);
                        variables.put("newPlanName", newPlanName);
                        variables.put("duration", subscription.getDuration());
                        variables.put("devices", subscription.getMultiLoginCount());
                        variables.put("formattedExpiry", subscription.getExpiresAt().format(
                                        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")));

                        multiChannelService.sendNotification(
                                        user,
                                        "subscription-plan-changed",
                                        NotificationCategory.BILLING,
                                        variables,
                                        message -> {
                                                switch (message.getChannel()) {
                                                        case SMS:
                                                                return messageSource.getMessage(
                                                                                "sms.subscription.plan.changed",
                                                                                new Object[] { oldPlanName, newPlanName },
                                                                                locale);
                                                        case WHATSAPP:
                                                                return messageSource.getMessage(
                                                                                "whatsapp.subscription.plan.changed",
                                                                                new Object[] { userName, oldPlanName, newPlanName },
                                                                                locale);
                                                        case TELEGRAM:
                                                                return messageSource.getMessage(
                                                                                "telegram.subscription.plan.changed",
                                                                                new Object[] { userName, oldPlanName, newPlanName },
                                                                                locale);
                                                        case FCM:
                                                                Map<String, Object> fcmData = new HashMap<>();
                                                                fcmData.put("title", messageSource.getMessage(
                                                                                "fcm.subscription.plan.changed.title", null, locale));
                                                                fcmData.put("body", messageSource.getMessage(
                                                                                "fcm.subscription.plan.changed.body",
                                                                                new Object[] { newPlanName }, locale));
                                                                fcmData.put("type", "subscription_plan_changed");
                                                                fcmData.put("click_action", "FLUTTER_NOTIFICATION_CLICK");
                                                                fcmData.put("custom_action", "OPEN_SUBSCRIPTION");
                                                                try {
                                                                        return objectMapper.writeValueAsString(fcmData);
                                                                } catch (JsonProcessingException e) {
                                                                        log.error("Error serializing FCM plan changed notification", e);
                                                                        return null;
                                                                }
                                                        case EMAIL:
                                                        default:
                                                                emailService.sendTemplatedEmail(
                                                                                user.getEmail(),
                                                                                "subscription-plan-changed",
                                                                                variables,
                                                                                locale);
                                                                return null;
                                                }
                                        });
                        log.info("Sent subscription plan changed notification to user: {}", user.getEmail());
                } catch (Exception e) {
                        log.error("Failed to send subscription plan changed notification to user: {}", user.getEmail(), e);
                }
        }

        /**
         * Send refund processed notification
         */
        public void sendRefundProcessedNotification(User user, java.math.BigDecimal amount, String reason) {
                try {
                        Locale locale = localeResolverService.resolveUserLocale(user);
                        String userName = getUserDisplayName(user);

                        Map<String, Object> variables = new HashMap<>();
                        variables.put("userName", userName);
                        variables.put("amount", amount != null ? amount.toString() : "0.00");
                        variables.put("reason", reason != null ? reason : "Refund processed");

                        multiChannelService.sendNotification(
                                        user,
                                        "refund-processed",
                                        NotificationCategory.BILLING,
                                        variables,
                                        message -> {
                                                switch (message.getChannel()) {
                                                        case SMS:
                                                                return messageSource.getMessage(
                                                                                "sms.refund.processed",
                                                                                new Object[] { amount },
                                                                                locale);
                                                        case WHATSAPP:
                                                                return messageSource.getMessage(
                                                                                "whatsapp.refund.processed",
                                                                                new Object[] { userName, amount },
                                                                                locale);
                                                        case TELEGRAM:
                                                                return messageSource.getMessage(
                                                                                "telegram.refund.processed",
                                                                                new Object[] { userName, amount },
                                                                                locale);
                                                        case FCM:
                                                                Map<String, Object> fcmData = new HashMap<>();
                                                                fcmData.put("title", messageSource.getMessage(
                                                                                "fcm.refund.processed.title", null, locale));
                                                                fcmData.put("body", messageSource.getMessage(
                                                                                "fcm.refund.processed.body",
                                                                                new Object[] { amount }, locale));
                                                                fcmData.put("type", "refund_processed");
                                                                fcmData.put("click_action", "FLUTTER_NOTIFICATION_CLICK");
                                                                fcmData.put("custom_action", "OPEN_BILLING");
                                                                try {
                                                                        return objectMapper.writeValueAsString(fcmData);
                                                                } catch (JsonProcessingException e) {
                                                                        log.error("Error serializing FCM refund notification", e);
                                                                        return null;
                                                                }
                                                        case EMAIL:
                                                        default:
                                                                emailService.sendTemplatedEmail(
                                                                                user.getEmail(),
                                                                                "refund-processed",
                                                                                variables,
                                                                                locale);
                                                                return null;
                                                }
                                        });
                        log.info("Sent refund processed notification to user: {}", user.getEmail());
                } catch (Exception e) {
                        log.error("Failed to send refund processed notification to user: {}", user.getEmail(), e);
                }
        }

        /**
         * Send new device login notification (security)
         */
        public void sendNewDeviceLoginNotification(User user, String deviceInfo, String ipAddress, String location) {
                try {
                        Locale locale = localeResolverService.resolveUserLocale(user);
                        String userName = getUserDisplayName(user);
                        String loginTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));

                        Map<String, Object> variables = new HashMap<>();
                        variables.put("userName", userName);
                        variables.put("deviceInfo", deviceInfo != null ? deviceInfo : "Unknown device");
                        variables.put("ipAddress", ipAddress != null ? ipAddress : "Unknown");
                        variables.put("location", location != null ? location : "Unknown location");
                        variables.put("loginTime", loginTime);
                        variables.put("mailtoLink", createMailtoLink(
                                        "Unauthorized Login Report",
                                        String.format("I did not log in from device: %s, IP: %s at %s", deviceInfo, ipAddress, loginTime)));

                        multiChannelService.sendNotification(
                                        user,
                                        "new-device-login",
                                        NotificationCategory.SECURITY,
                                        variables,
                                        message -> {
                                                switch (message.getChannel()) {
                                                        case SMS:
                                                                return messageSource.getMessage(
                                                                                "sms.new.device.login",
                                                                                new Object[] { deviceInfo, loginTime },
                                                                                locale);
                                                        case WHATSAPP:
                                                                return messageSource.getMessage(
                                                                                "whatsapp.new.device.login",
                                                                                new Object[] { userName, deviceInfo, ipAddress, loginTime },
                                                                                locale);
                                                        case TELEGRAM:
                                                                return messageSource.getMessage(
                                                                                "telegram.new.device.login",
                                                                                new Object[] { userName, deviceInfo, ipAddress, loginTime },
                                                                                locale);
                                                        case FCM:
                                                                Map<String, Object> fcmData = new HashMap<>();
                                                                fcmData.put("title", messageSource.getMessage(
                                                                                "fcm.new.device.login.title", null, locale));
                                                                fcmData.put("body", messageSource.getMessage(
                                                                                "fcm.new.device.login.body",
                                                                                new Object[] { deviceInfo }, locale));
                                                                fcmData.put("type", "new_device_login");
                                                                fcmData.put("click_action", "FLUTTER_NOTIFICATION_CLICK");
                                                                fcmData.put("custom_action", "OPEN_SECURITY_SETTINGS");
                                                                try {
                                                                        return objectMapper.writeValueAsString(fcmData);
                                                                } catch (JsonProcessingException e) {
                                                                        log.error("Error serializing FCM new device login notification", e);
                                                                        return null;
                                                                }
                                                        case EMAIL:
                                                        default:
                                                                emailService.sendTemplatedEmail(
                                                                                user.getEmail(),
                                                                                "new-device-login",
                                                                                variables,
                                                                                locale);
                                                                return null;
                                                }
                                        });
                        log.info("Sent new device login notification to user: {}", user.getEmail());
                } catch (Exception e) {
                        log.error("Failed to send new device login notification to user: {}", user.getEmail(), e);
                }
        }

        /**
         * Send account locked notification (security)
         */
        public void sendAccountLockedNotification(User user, String reason, int lockDurationMinutes) {
                try {
                        Locale locale = localeResolverService.resolveUserLocale(user);
                        String userName = getUserDisplayName(user);

                        Map<String, Object> variables = new HashMap<>();
                        variables.put("userName", userName);
                        variables.put("reason", reason != null ? reason : "Too many failed login attempts");
                        variables.put("lockDuration", lockDurationMinutes);
                        variables.put("mailtoLink", createMailtoLink(
                                        "Account Unlock Request",
                                        "My account has been locked. Please help me unlock it."));

                        multiChannelService.sendNotification(
                                        user,
                                        "account-locked",
                                        NotificationCategory.SECURITY,
                                        variables,
                                        message -> {
                                                switch (message.getChannel()) {
                                                        case SMS:
                                                                return messageSource.getMessage(
                                                                                "sms.account.locked",
                                                                                new Object[] { lockDurationMinutes },
                                                                                locale);
                                                        case WHATSAPP:
                                                                return messageSource.getMessage(
                                                                                "whatsapp.account.locked",
                                                                                new Object[] { userName, lockDurationMinutes },
                                                                                locale);
                                                        case TELEGRAM:
                                                                return messageSource.getMessage(
                                                                                "telegram.account.locked",
                                                                                new Object[] { userName, lockDurationMinutes },
                                                                                locale);
                                                        case FCM:
                                                                Map<String, Object> fcmData = new HashMap<>();
                                                                fcmData.put("title", messageSource.getMessage(
                                                                                "fcm.account.locked.title", null, locale));
                                                                fcmData.put("body", messageSource.getMessage(
                                                                                "fcm.account.locked.body",
                                                                                new Object[] { lockDurationMinutes }, locale));
                                                                fcmData.put("type", "account_locked");
                                                                fcmData.put("click_action", "FLUTTER_NOTIFICATION_CLICK");
                                                                fcmData.put("custom_action", "OPEN_SECURITY_SETTINGS");
                                                                try {
                                                                        return objectMapper.writeValueAsString(fcmData);
                                                                } catch (JsonProcessingException e) {
                                                                        log.error("Error serializing FCM account locked notification", e);
                                                                        return null;
                                                                }
                                                        case EMAIL:
                                                        default:
                                                                emailService.sendTemplatedEmail(
                                                                                user.getEmail(),
                                                                                "account-locked",
                                                                                variables,
                                                                                locale);
                                                                return null;
                                                }
                                        });
                        log.info("Sent account locked notification to user: {}", user.getEmail());
                } catch (Exception e) {
                        log.error("Failed to send account locked notification to user: {}", user.getEmail(), e);
                }
        }

        /**
         * Send email/phone changed notification (security)
         */
        public void sendContactInfoChangedNotification(User user, String changeType, String oldValue, String newValue) {
                try {
                        Locale locale = localeResolverService.resolveUserLocale(user);
                        String userName = getUserDisplayName(user);

                        Map<String, Object> variables = new HashMap<>();
                        variables.put("userName", userName);
                        variables.put("changeType", changeType); // "email" or "phone"
                        variables.put("oldValue", oldValue != null ? maskSensitiveData(oldValue) : "N/A");
                        variables.put("newValue", newValue != null ? maskSensitiveData(newValue) : "N/A");
                        variables.put("changeTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")));
                        variables.put("mailtoLink", createMailtoLink(
                                        "Unauthorized " + changeType + " Change Report",
                                        String.format("I did not change my %s. Please help secure my account.", changeType)));

                        // Send to old email/phone if available
                        String notificationEmail = "email".equals(changeType) && oldValue != null ? oldValue : user.getEmail();

                        multiChannelService.sendNotification(
                                        notificationEmail,
                                        "contact-info-changed",
                                        NotificationCategory.SECURITY,
                                        variables,
                                        message -> {
                                                switch (message.getChannel()) {
                                                        case SMS:
                                                                return messageSource.getMessage(
                                                                                "sms.contact.info.changed",
                                                                                new Object[] { changeType },
                                                                                locale);
                                                        case WHATSAPP:
                                                                return messageSource.getMessage(
                                                                                "whatsapp.contact.info.changed",
                                                                                new Object[] { userName, changeType },
                                                                                locale);
                                                        case TELEGRAM:
                                                                return messageSource.getMessage(
                                                                                "telegram.contact.info.changed",
                                                                                new Object[] { userName, changeType },
                                                                                locale);
                                                        case FCM:
                                                                Map<String, Object> fcmData = new HashMap<>();
                                                                fcmData.put("title", messageSource.getMessage(
                                                                                "fcm.contact.info.changed.title", null, locale));
                                                                fcmData.put("body", messageSource.getMessage(
                                                                                "fcm.contact.info.changed.body",
                                                                                new Object[] { changeType }, locale));
                                                                fcmData.put("type", "contact_info_changed");
                                                                fcmData.put("click_action", "FLUTTER_NOTIFICATION_CLICK");
                                                                fcmData.put("custom_action", "OPEN_ACCOUNT_SETTINGS");
                                                                try {
                                                                        return objectMapper.writeValueAsString(fcmData);
                                                                } catch (JsonProcessingException e) {
                                                                        log.error("Error serializing FCM contact info changed notification", e);
                                                                        return null;
                                                                }
                                                        case EMAIL:
                                                        default:
                                                                emailService.sendTemplatedEmail(
                                                                                notificationEmail,
                                                                                "contact-info-changed",
                                                                                variables,
                                                                                locale);
                                                                return null;
                                                }
                                        });
                        log.info("Sent contact info changed notification to user: {}", user.getEmail());
                } catch (Exception e) {
                        log.error("Failed to send contact info changed notification to user: {}", user.getEmail(), e);
                }
        }

        /**
         * Send bandwidth warning notification (80% usage)
         */
        public void sendBandwidthWarningNotification(User user, double usagePercent, String limitType) {
                try {
                        Locale locale = localeResolverService.resolveUserLocale(user);
                        String userName = getUserDisplayName(user);

                        Map<String, Object> variables = new HashMap<>();
                        variables.put("userName", userName);
                        variables.put("usagePercent", String.format("%.0f", usagePercent));
                        variables.put("limitType", limitType); // "daily" or "total"

                        multiChannelService.sendNotification(
                                        user,
                                        "bandwidth-warning",
                                        NotificationCategory.ACCOUNT,
                                        variables,
                                        message -> {
                                                switch (message.getChannel()) {
                                                        case SMS:
                                                                return messageSource.getMessage(
                                                                                "sms.bandwidth.warning",
                                                                                new Object[] { usagePercent, limitType },
                                                                                locale);
                                                        case WHATSAPP:
                                                                return messageSource.getMessage(
                                                                                "whatsapp.bandwidth.warning",
                                                                                new Object[] { userName, usagePercent, limitType },
                                                                                locale);
                                                        case TELEGRAM:
                                                                return messageSource.getMessage(
                                                                                "telegram.bandwidth.warning",
                                                                                new Object[] { userName, usagePercent, limitType },
                                                                                locale);
                                                        case FCM:
                                                                Map<String, Object> fcmData = new HashMap<>();
                                                                fcmData.put("title", messageSource.getMessage(
                                                                                "fcm.bandwidth.warning.title", null, locale));
                                                                fcmData.put("body", messageSource.getMessage(
                                                                                "fcm.bandwidth.warning.body",
                                                                                new Object[] { usagePercent }, locale));
                                                                fcmData.put("type", "bandwidth_warning");
                                                                fcmData.put("click_action", "FLUTTER_NOTIFICATION_CLICK");
                                                                fcmData.put("custom_action", "OPEN_USAGE_STATS");
                                                                try {
                                                                        return objectMapper.writeValueAsString(fcmData);
                                                                } catch (JsonProcessingException e) {
                                                                        log.error("Error serializing FCM bandwidth warning notification", e);
                                                                        return null;
                                                                }
                                                        case EMAIL:
                                                        default:
                                                                emailService.sendTemplatedEmail(
                                                                                user.getEmail(),
                                                                                "bandwidth-warning",
                                                                                variables,
                                                                                locale);
                                                                return null;
                                                }
                                        });
                        log.info("Sent bandwidth warning notification to user: {}", user.getEmail());
                } catch (Exception e) {
                        log.error("Failed to send bandwidth warning notification to user: {}", user.getEmail(), e);
                }
        }

        /**
         * Send bandwidth exceeded notification
         */
        public void sendBandwidthExceededNotification(User user, String limitType) {
                try {
                        Locale locale = localeResolverService.resolveUserLocale(user);
                        String userName = getUserDisplayName(user);

                        Map<String, Object> variables = new HashMap<>();
                        variables.put("userName", userName);
                        variables.put("limitType", limitType); // "daily" or "total"

                        multiChannelService.sendNotification(
                                        user,
                                        "bandwidth-exceeded",
                                        NotificationCategory.ACCOUNT,
                                        variables,
                                        message -> {
                                                switch (message.getChannel()) {
                                                        case SMS:
                                                                return messageSource.getMessage(
                                                                                "sms.bandwidth.exceeded",
                                                                                new Object[] { limitType },
                                                                                locale);
                                                        case WHATSAPP:
                                                                return messageSource.getMessage(
                                                                                "whatsapp.bandwidth.exceeded",
                                                                                new Object[] { userName, limitType },
                                                                                locale);
                                                        case TELEGRAM:
                                                                return messageSource.getMessage(
                                                                                "telegram.bandwidth.exceeded",
                                                                                new Object[] { userName, limitType },
                                                                                locale);
                                                        case FCM:
                                                                Map<String, Object> fcmData = new HashMap<>();
                                                                fcmData.put("title", messageSource.getMessage(
                                                                                "fcm.bandwidth.exceeded.title", null, locale));
                                                                fcmData.put("body", messageSource.getMessage(
                                                                                "fcm.bandwidth.exceeded.body",
                                                                                new Object[] { limitType }, locale));
                                                                fcmData.put("type", "bandwidth_exceeded");
                                                                fcmData.put("click_action", "FLUTTER_NOTIFICATION_CLICK");
                                                                fcmData.put("custom_action", "UPGRADE_PLAN");
                                                                try {
                                                                        return objectMapper.writeValueAsString(fcmData);
                                                                } catch (JsonProcessingException e) {
                                                                        log.error("Error serializing FCM bandwidth exceeded notification", e);
                                                                        return null;
                                                                }
                                                        case EMAIL:
                                                        default:
                                                                emailService.sendTemplatedEmail(
                                                                                user.getEmail(),
                                                                                "bandwidth-exceeded",
                                                                                variables,
                                                                                locale);
                                                                return null;
                                                }
                                        });
                        log.info("Sent bandwidth exceeded notification to user: {}", user.getEmail());
                } catch (Exception e) {
                        log.error("Failed to send bandwidth exceeded notification to user: {}", user.getEmail(), e);
                }
        }

        /**
         * Send simultaneous login limit reached notification
         */
        public void sendLoginLimitReachedNotification(User user, int maxDevices) {
                try {
                        Locale locale = localeResolverService.resolveUserLocale(user);
                        String userName = getUserDisplayName(user);

                        Map<String, Object> variables = new HashMap<>();
                        variables.put("userName", userName);
                        variables.put("maxDevices", maxDevices);

                        multiChannelService.sendNotification(
                                        user,
                                        "login-limit-reached",
                                        NotificationCategory.ACCOUNT,
                                        variables,
                                        message -> {
                                                switch (message.getChannel()) {
                                                        case SMS:
                                                                return messageSource.getMessage(
                                                                                "sms.login.limit.reached",
                                                                                new Object[] { maxDevices },
                                                                                locale);
                                                        case WHATSAPP:
                                                                return messageSource.getMessage(
                                                                                "whatsapp.login.limit.reached",
                                                                                new Object[] { userName, maxDevices },
                                                                                locale);
                                                        case TELEGRAM:
                                                                return messageSource.getMessage(
                                                                                "telegram.login.limit.reached",
                                                                                new Object[] { userName, maxDevices },
                                                                                locale);
                                                        case FCM:
                                                                Map<String, Object> fcmData = new HashMap<>();
                                                                fcmData.put("title", messageSource.getMessage(
                                                                                "fcm.login.limit.reached.title", null, locale));
                                                                fcmData.put("body", messageSource.getMessage(
                                                                                "fcm.login.limit.reached.body",
                                                                                new Object[] { maxDevices }, locale));
                                                                fcmData.put("type", "login_limit_reached");
                                                                fcmData.put("click_action", "FLUTTER_NOTIFICATION_CLICK");
                                                                fcmData.put("custom_action", "MANAGE_DEVICES");
                                                                try {
                                                                        return objectMapper.writeValueAsString(fcmData);
                                                                } catch (JsonProcessingException e) {
                                                                        log.error("Error serializing FCM login limit notification", e);
                                                                        return null;
                                                                }
                                                        case EMAIL:
                                                        default:
                                                                emailService.sendTemplatedEmail(
                                                                                user.getEmail(),
                                                                                "login-limit-reached",
                                                                                variables,
                                                                                locale);
                                                                return null;
                                                }
                                        });
                        log.info("Sent login limit reached notification to user: {}", user.getEmail());
                } catch (Exception e) {
                        log.error("Failed to send login limit reached notification to user: {}", user.getEmail(), e);
                }
        }

        /**
         * Send subscription grace period notification (payment failed but still active)
         */
        public void sendSubscriptionGracePeriodNotification(User user, UserSubscription subscription, int graceDaysRemaining) {
                try {
                        Locale locale = localeResolverService.resolveUserLocale(user);
                        String userName = getUserDisplayName(user);

                        Map<String, Object> variables = new HashMap<>();
                        variables.put("userName", userName);
                        variables.put("graceDaysRemaining", graceDaysRemaining);
                        variables.put("planName", subscription.getGroup() != null ? subscription.getGroup().getName() : "Subscription");

                        multiChannelService.sendNotification(
                                        user,
                                        "subscription-grace-period",
                                        NotificationCategory.BILLING,
                                        variables,
                                        message -> {
                                                switch (message.getChannel()) {
                                                        case SMS:
                                                                return messageSource.getMessage(
                                                                                "sms.subscription.grace.period",
                                                                                new Object[] { graceDaysRemaining },
                                                                                locale);
                                                        case WHATSAPP:
                                                                return messageSource.getMessage(
                                                                                "whatsapp.subscription.grace.period",
                                                                                new Object[] { userName, graceDaysRemaining },
                                                                                locale);
                                                        case TELEGRAM:
                                                                return messageSource.getMessage(
                                                                                "telegram.subscription.grace.period",
                                                                                new Object[] { userName, graceDaysRemaining },
                                                                                locale);
                                                        case FCM:
                                                                Map<String, Object> fcmData = new HashMap<>();
                                                                fcmData.put("title", messageSource.getMessage(
                                                                                "fcm.subscription.grace.period.title", null, locale));
                                                                fcmData.put("body", messageSource.getMessage(
                                                                                "fcm.subscription.grace.period.body",
                                                                                new Object[] { graceDaysRemaining }, locale));
                                                                fcmData.put("type", "subscription_grace_period");
                                                                fcmData.put("click_action", "FLUTTER_NOTIFICATION_CLICK");
                                                                fcmData.put("custom_action", "UPDATE_PAYMENT_METHOD");
                                                                try {
                                                                        return objectMapper.writeValueAsString(fcmData);
                                                                } catch (JsonProcessingException e) {
                                                                        log.error("Error serializing FCM grace period notification", e);
                                                                        return null;
                                                                }
                                                        case EMAIL:
                                                        default:
                                                                emailService.sendTemplatedEmail(
                                                                                user.getEmail(),
                                                                                "subscription-grace-period",
                                                                                variables,
                                                                                locale);
                                                                return null;
                                                }
                                        });
                        log.info("Sent subscription grace period notification to user: {}", user.getEmail());
                } catch (Exception e) {
                        log.error("Failed to send subscription grace period notification to user: {}", user.getEmail(), e);
                }
        }

        /**
         * Send subscription on hold notification (Google Play)
         */
        public void sendSubscriptionOnHoldNotification(User user, UserSubscription subscription) {
                try {
                        Locale locale = localeResolverService.resolveUserLocale(user);
                        String userName = getUserDisplayName(user);

                        Map<String, Object> variables = new HashMap<>();
                        variables.put("userName", userName);
                        variables.put("planName", subscription.getGroup() != null ? subscription.getGroup().getName() : "Subscription");

                        multiChannelService.sendNotification(
                                        user,
                                        "subscription-on-hold",
                                        NotificationCategory.BILLING,
                                        variables,
                                        message -> {
                                                switch (message.getChannel()) {
                                                        case SMS:
                                                                return messageSource.getMessage(
                                                                                "sms.subscription.on.hold",
                                                                                null,
                                                                                locale);
                                                        case WHATSAPP:
                                                                return messageSource.getMessage(
                                                                                "whatsapp.subscription.on.hold",
                                                                                new Object[] { userName },
                                                                                locale);
                                                        case TELEGRAM:
                                                                return messageSource.getMessage(
                                                                                "telegram.subscription.on.hold",
                                                                                new Object[] { userName },
                                                                                locale);
                                                        case FCM:
                                                                Map<String, Object> fcmData = new HashMap<>();
                                                                fcmData.put("title", messageSource.getMessage(
                                                                                "fcm.subscription.on.hold.title", null, locale));
                                                                fcmData.put("body", messageSource.getMessage(
                                                                                "fcm.subscription.on.hold.body", null, locale));
                                                                fcmData.put("type", "subscription_on_hold");
                                                                fcmData.put("click_action", "FLUTTER_NOTIFICATION_CLICK");
                                                                fcmData.put("custom_action", "UPDATE_PAYMENT_METHOD");
                                                                try {
                                                                        return objectMapper.writeValueAsString(fcmData);
                                                                } catch (JsonProcessingException e) {
                                                                        log.error("Error serializing FCM on hold notification", e);
                                                                        return null;
                                                                }
                                                        case EMAIL:
                                                        default:
                                                                emailService.sendTemplatedEmail(
                                                                                user.getEmail(),
                                                                                "subscription-on-hold",
                                                                                variables,
                                                                                locale);
                                                                return null;
                                                }
                                        });
                        log.info("Sent subscription on hold notification to user: {}", user.getEmail());
                } catch (Exception e) {
                        log.error("Failed to send subscription on hold notification to user: {}", user.getEmail(), e);
                }
        }

        /**
         * Helper method to mask sensitive data like email or phone
         */
        private String maskSensitiveData(String data) {
                if (data == null || data.length() < 4) {
                        return "****";
                }
                if (data.contains("@")) {
                        // Email masking: show first 2 chars + domain
                        int atIndex = data.indexOf("@");
                        if (atIndex > 2) {
                                return data.substring(0, 2) + "***" + data.substring(atIndex);
                        }
                }
                // Phone/other masking: show first 2 and last 2 chars
                return data.substring(0, 2) + "***" + data.substring(data.length() - 2);
        }
}