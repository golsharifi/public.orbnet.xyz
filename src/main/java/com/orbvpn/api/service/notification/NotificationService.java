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

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

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

        private static final String EVERY_DAY_8AM = "0 0 8 * * ?";
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
        @Scheduled(cron = EVERY_DAY_8AM)
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
        @Scheduled(cron = EVERY_DAY_8AM)
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
        @Scheduled(cron = EVERY_DAY_8AM)
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
                variables.put("verificationCode", verificationCode);

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
}