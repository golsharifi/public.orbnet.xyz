package com.orbvpn.api.service;

import com.orbvpn.api.domain.entity.GiftCard;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserExtraLogins;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.service.notification.NotificationService;
import com.orbvpn.api.service.webhook.WebhookEventCreator;
import com.orbvpn.api.service.webhook.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;

/**
 * Helper service for sending notifications and webhooks asynchronously.
 * This prevents blocking the main request thread while sending emails or processing webhooks.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncNotificationHelper {

    private final NotificationService notificationService;
    private final WebhookService webhookService;
    private final WebhookEventCreator webhookEventCreator;

    // ==================== WEBHOOK METHODS ====================

    /**
     * Send a webhook asynchronously
     */
    @Async
    public void sendWebhookAsync(String eventType, Map<String, Object> payload) {
        try {
            webhookService.processWebhook(eventType, payload);
            log.debug("Async webhook sent: {}", eventType);
        } catch (Exception e) {
            log.error("Failed to send async webhook {}: {}", eventType, e.getMessage());
        }
    }

    /**
     * Send user-related webhook asynchronously
     */
    @Async
    public void sendUserWebhookAsync(User user, String eventType) {
        try {
            webhookService.processWebhook(eventType, webhookEventCreator.createUserPayload(user, eventType));
            log.debug("Async user webhook sent: {} for user {}", eventType, user.getId());
        } catch (Exception e) {
            log.error("Failed to send async user webhook {} for user {}: {}", eventType, user.getId(), e.getMessage());
        }
    }

    /**
     * Send user-related webhook with extra data asynchronously
     */
    @Async
    public void sendUserWebhookWithExtraAsync(User user, String eventType, Map<String, Object> extraData) {
        try {
            webhookService.processWebhook(eventType, webhookEventCreator.createPayloadWithExtra(user, eventType, extraData));
            log.debug("Async user webhook with extra data sent: {} for user {}", eventType, user.getId());
        } catch (Exception e) {
            log.error("Failed to send async user webhook {} for user {}: {}", eventType, user.getId(), e.getMessage());
        }
    }

    /**
     * Send subscription-related webhook asynchronously
     */
    @Async
    public void sendSubscriptionWebhookAsync(UserSubscription subscription, String eventType) {
        try {
            webhookService.processWebhook(eventType, webhookEventCreator.createSubscriptionPayload(subscription));
            log.debug("Async subscription webhook sent: {} for subscription {}", eventType, subscription.getId());
        } catch (Exception e) {
            log.error("Failed to send async subscription webhook {} for subscription {}: {}", eventType, subscription.getId(), e.getMessage());
        }
    }

    // ==================== NOTIFICATION METHODS ====================

    /**
     * Send welcome email to new user created by admin (with subscription)
     */
    @Async
    public void sendWelcomeEmailWithSubscriptionAsync(User user, UserSubscription subscription, String password) {
        try {
            notificationService.welcomingNewUsersCreatedByAdmin(user, subscription, password);
            log.debug("Async welcome email (with subscription) sent to user {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to send async welcome email to user {}: {}", user.getId(), e.getMessage());
        }
    }

    /**
     * Send welcome email without subscription asynchronously
     */
    @Async
    public void sendWelcomeEmailNoSubscriptionAsync(User user, String password) {
        try {
            notificationService.welcomingNewUsersWithoutSubscription(user, password);
            log.debug("Async welcome email (no subscription) sent to user {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to send async welcome email to user {}: {}", user.getId(), e.getMessage());
        }
    }

    /**
     * Send password reset email asynchronously
     */
    @Async
    public void sendPasswordResetEmailAsync(User user, String token) {
        try {
            notificationService.sendTokenCodeToUser(user, token);
            log.debug("Async password reset email sent to user {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to send async password reset email to user {}: {}", user.getId(), e.getMessage());
        }
    }

    /**
     * Send magic login code asynchronously
     */
    @Async
    public void sendMagicLoginCodeAsync(User user, String code) {
        try {
            notificationService.sendMagicLoginCode(user, code);
            log.debug("Async magic login code sent to user {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to send async magic login code to user {}: {}", user.getId(), e.getMessage());
        }
    }

    /**
     * Send magic link asynchronously (URL-based passwordless login)
     */
    @Async
    public void sendMagicLinkAsync(User user, String token) {
        try {
            notificationService.sendMagicLink(user, token);
            log.debug("Async magic link sent to user {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to send async magic link to user {}: {}", user.getId(), e.getMessage());
        }
    }

    /**
     * Send verification email asynchronously
     */
    @Async
    public void sendVerificationEmailAsync(String email, String code) {
        try {
            notificationService.sendVerificationEmail(email, code, null);
            log.debug("Async verification email sent to {}", email);
        } catch (Exception e) {
            log.error("Failed to send async verification email to {}: {}", email, e.getMessage());
        }
    }

    /**
     * Send verification email with locale asynchronously
     */
    @Async
    public void sendVerificationEmailAsync(String email, String code, Locale locale) {
        try {
            notificationService.sendVerificationEmail(email, code, locale);
            log.debug("Async verification email sent to {}", email);
        } catch (Exception e) {
            log.error("Failed to send async verification email to {}: {}", email, e.getMessage());
        }
    }

    /**
     * Send success verification email asynchronously
     */
    @Async
    public void sendSuccessVerificationEmailAsync(String email, Locale locale) {
        try {
            notificationService.sendSuccessVerificationEmail(email, locale);
            log.debug("Async success verification email sent to {}", email);
        } catch (Exception e) {
            log.error("Failed to send async success verification email to {}: {}", email, e.getMessage());
        }
    }

    /**
     * Send trial ending notification asynchronously
     */
    @Async
    public void sendTrialEndingNotificationAsync(User user, UserSubscription subscription, LocalDateTime trialEnd) {
        try {
            notificationService.sendTrialEndingNotification(user, subscription, trialEnd);
            log.debug("Async trial ending notification sent to user {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to send async trial ending notification to user {}: {}", user.getId(), e.getMessage());
        }
    }

    /**
     * Send payment failed notification asynchronously
     */
    @Async
    public void sendPaymentFailedNotificationAsync(User user, UserSubscription subscription) {
        try {
            notificationService.sendPaymentFailedNotification(user, subscription);
            log.debug("Async payment failed notification sent to user {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to send async payment failed notification to user {}: {}", user.getId(), e.getMessage());
        }
    }

    /**
     * Send password reset (with token) notification asynchronously
     */
    @Async
    public void sendResetPasswordAsync(User user, String token) {
        try {
            notificationService.resetPassword(user, token);
            log.debug("Async reset password email sent to user {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to send async reset password email to user {}: {}", user.getId(), e.getMessage());
        }
    }

    /**
     * Send password reset done notification asynchronously
     */
    @Async
    public void sendResetPasswordDoneAsync(User user) {
        try {
            notificationService.resetPasswordDone(user);
            log.debug("Async reset password done email sent to user {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to send async reset password done email to user {}: {}", user.getId(), e.getMessage());
        }
    }

    /**
     * Send admin password reset notification asynchronously
     * (when admin/reseller resets a user's password to a new random password)
     */
    @Async
    public void sendAdminPasswordResetNotificationAsync(User user, String newPassword) {
        try {
            notificationService.sendAdminPasswordResetNotification(user, newPassword);
            log.debug("Async admin password reset notification sent to user {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to send async admin password reset notification to user {}: {}", user.getId(), e.getMessage());
        }
    }

    /**
     * Send subscription renewal notification asynchronously
     */
    @Async
    public void sendSubscriptionRenewalNotificationAsync(User user, UserSubscription subscription) {
        try {
            notificationService.sendSubscriptionRenewalNotification(user, subscription);
            log.debug("Async subscription renewal notification sent to user {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to send async subscription renewal notification to user {}: {}", user.getId(), e.getMessage());
        }
    }

    /**
     * Send gift card redemption notification asynchronously
     */
    @Async
    public void sendGiftCardRedemptionNotificationAsync(User user, GiftCard giftCard) {
        try {
            notificationService.sendGiftCardRedemptionNotification(user, giftCard);
            log.debug("Async gift card redemption notification sent to user {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to send async gift card redemption notification to user {}: {}", user.getId(), e.getMessage());
        }
    }

    /**
     * Send extra logins confirmation asynchronously
     */
    @Async
    public void sendExtraLoginsConfirmationAsync(User user, UserExtraLogins extraLogins) {
        try {
            notificationService.sendExtraLoginsConfirmation(user, extraLogins);
            log.debug("Async extra logins confirmation sent to user {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to send async extra logins confirmation to user {}: {}", user.getId(), e.getMessage());
        }
    }

    /**
     * Send extra logins gift notification asynchronously
     */
    @Async
    public void sendExtraLoginsGiftNotificationAsync(User sender, User recipient, UserExtraLogins gift) {
        try {
            notificationService.sendExtraLoginsGiftNotification(sender, recipient, gift);
            log.debug("Async extra logins gift notification sent from user {} to user {}", sender.getId(), recipient.getId());
        } catch (Exception e) {
            log.error("Failed to send async extra logins gift notification: {}", e.getMessage());
        }
    }

    /**
     * Send extra logins expiration reminder asynchronously
     */
    @Async
    public void sendExtraLoginsExpirationReminderAsync(User user, UserExtraLogins extraLogins, int daysRemaining) {
        try {
            notificationService.sendExtraLoginsExpirationReminder(user, extraLogins, daysRemaining);
            log.debug("Async extra logins expiration reminder sent to user {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to send async extra logins expiration reminder to user {}: {}", user.getId(), e.getMessage());
        }
    }

    /**
     * Send extra logins expired notification asynchronously
     */
    @Async
    public void sendExtraLoginsExpiredNotificationAsync(User user, UserExtraLogins extraLogins) {
        try {
            notificationService.sendExtraLoginsExpiredNotification(user, extraLogins);
            log.debug("Async extra logins expired notification sent to user {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to send async extra logins expired notification to user {}: {}", user.getId(), e.getMessage());
        }
    }

    /**
     * Send admin notification asynchronously
     */
    @Async
    public void sendAdminNotificationAsync(String message, String title, Map<String, String> metadata) {
        try {
            notificationService.sendAdminNotification(message, title, metadata);
            log.debug("Async admin notification sent: {}", title);
        } catch (Exception e) {
            log.error("Failed to send async admin notification: {}", e.getMessage());
        }
    }

    /**
     * Send invoice email asynchronously
     */
    @Async
    public void sendInvoiceEmailAsync(String toEmail, Map<String, Object> variables, Locale locale) {
        try {
            notificationService.sendInvoiceEmail(toEmail, variables, locale);
            log.debug("Async invoice email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send async invoice email to {}: {}", toEmail, e.getMessage());
        }
    }

    /**
     * Send gift card cancellation notification asynchronously
     */
    @Async
    public void sendGiftCardCancellationNotificationAsync(GiftCard giftCard) {
        try {
            notificationService.sendGiftCardCancellationNotification(giftCard);
            log.debug("Async gift card cancellation notification sent for gift card {}", giftCard.getId());
        } catch (Exception e) {
            log.error("Failed to send async gift card cancellation notification: {}", e.getMessage());
        }
    }

    // ==================== COMBINED METHODS ====================

    /**
     * Send both welcome email (without subscription) and user created webhook asynchronously
     */
    @Async
    public void sendUserCreatedNotificationsAsync(User user, String password) {
        try {
            notificationService.welcomingNewUsersWithoutSubscription(user, password);
            log.debug("Async welcome email sent to user {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to send async welcome email to user {}: {}", user.getId(), e.getMessage());
        }

        try {
            webhookService.processWebhook("USER_CREATED", webhookEventCreator.createUserPayload(user, "USER_CREATED"));
            log.debug("Async USER_CREATED webhook sent for user {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to send async USER_CREATED webhook for user {}: {}", user.getId(), e.getMessage());
        }
    }

    /**
     * Send subscription activated notifications asynchronously
     */
    @Async
    public void sendSubscriptionActivatedNotificationsAsync(User user, UserSubscription subscription) {
        try {
            webhookService.processWebhook("SUBSCRIPTION_ACTIVATED",
                webhookEventCreator.createSubscriptionPayload(subscription));
            log.debug("Async SUBSCRIPTION_ACTIVATED webhook sent for user {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to send async SUBSCRIPTION_ACTIVATED webhook for user {}: {}", user.getId(), e.getMessage());
        }
    }

    // ==================== NEW NOTIFICATION METHODS ====================

    /**
     * Send payment success notification asynchronously
     */
    @Async
    public void sendPaymentSuccessNotificationAsync(User user, UserSubscription subscription, BigDecimal amount) {
        try {
            notificationService.sendPaymentSuccessNotification(user, subscription, amount);
            log.debug("Async payment success notification sent to user {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to send async payment success notification to user {}: {}", user.getId(), e.getMessage());
        }
    }

    /**
     * Send subscription auto-renewed notification asynchronously
     */
    @Async
    public void sendSubscriptionAutoRenewedNotificationAsync(User user, UserSubscription subscription, BigDecimal amount) {
        try {
            notificationService.sendSubscriptionAutoRenewedNotification(user, subscription, amount);
            log.debug("Async subscription auto-renewed notification sent to user {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to send async subscription auto-renewed notification to user {}: {}", user.getId(), e.getMessage());
        }
    }

    /**
     * Send subscription plan changed notification asynchronously
     */
    @Async
    public void sendSubscriptionPlanChangedNotificationAsync(User user, UserSubscription subscription, String oldPlanName, String newPlanName) {
        try {
            notificationService.sendSubscriptionPlanChangedNotification(user, subscription, oldPlanName, newPlanName);
            log.debug("Async subscription plan changed notification sent to user {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to send async subscription plan changed notification to user {}: {}", user.getId(), e.getMessage());
        }
    }

    /**
     * Send refund processed notification asynchronously
     */
    @Async
    public void sendRefundProcessedNotificationAsync(User user, BigDecimal amount, String reason) {
        try {
            notificationService.sendRefundProcessedNotification(user, amount, reason);
            log.debug("Async refund processed notification sent to user {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to send async refund processed notification to user {}: {}", user.getId(), e.getMessage());
        }
    }

    /**
     * Send new device login notification asynchronously
     */
    @Async
    public void sendNewDeviceLoginNotificationAsync(User user, String deviceInfo, String ipAddress, String location) {
        try {
            notificationService.sendNewDeviceLoginNotification(user, deviceInfo, ipAddress, location);
            log.debug("Async new device login notification sent to user {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to send async new device login notification to user {}: {}", user.getId(), e.getMessage());
        }
    }

    /**
     * Send account locked notification asynchronously
     */
    @Async
    public void sendAccountLockedNotificationAsync(User user, String reason, int lockDurationMinutes) {
        try {
            notificationService.sendAccountLockedNotification(user, reason, lockDurationMinutes);
            log.debug("Async account locked notification sent to user {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to send async account locked notification to user {}: {}", user.getId(), e.getMessage());
        }
    }

    /**
     * Send contact info changed notification asynchronously
     */
    @Async
    public void sendContactInfoChangedNotificationAsync(User user, String changeType, String oldValue, String newValue) {
        try {
            notificationService.sendContactInfoChangedNotification(user, changeType, oldValue, newValue);
            log.debug("Async contact info changed notification sent to user {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to send async contact info changed notification to user {}: {}", user.getId(), e.getMessage());
        }
    }

    /**
     * Send bandwidth warning notification asynchronously
     */
    @Async
    public void sendBandwidthWarningNotificationAsync(User user, double usagePercent, String limitType) {
        try {
            notificationService.sendBandwidthWarningNotification(user, usagePercent, limitType);
            log.debug("Async bandwidth warning notification sent to user {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to send async bandwidth warning notification to user {}: {}", user.getId(), e.getMessage());
        }
    }

    /**
     * Send bandwidth exceeded notification asynchronously
     */
    @Async
    public void sendBandwidthExceededNotificationAsync(User user, String limitType) {
        try {
            notificationService.sendBandwidthExceededNotification(user, limitType);
            log.debug("Async bandwidth exceeded notification sent to user {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to send async bandwidth exceeded notification to user {}: {}", user.getId(), e.getMessage());
        }
    }

    /**
     * Send login limit reached notification asynchronously
     */
    @Async
    public void sendLoginLimitReachedNotificationAsync(User user, int maxDevices) {
        try {
            notificationService.sendLoginLimitReachedNotification(user, maxDevices);
            log.debug("Async login limit reached notification sent to user {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to send async login limit reached notification to user {}: {}", user.getId(), e.getMessage());
        }
    }

    /**
     * Send subscription grace period notification asynchronously
     */
    @Async
    public void sendSubscriptionGracePeriodNotificationAsync(User user, UserSubscription subscription, int graceDaysRemaining) {
        try {
            notificationService.sendSubscriptionGracePeriodNotification(user, subscription, graceDaysRemaining);
            log.debug("Async subscription grace period notification sent to user {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to send async subscription grace period notification to user {}: {}", user.getId(), e.getMessage());
        }
    }

    /**
     * Send subscription on hold notification asynchronously
     */
    @Async
    public void sendSubscriptionOnHoldNotificationAsync(User user, UserSubscription subscription) {
        try {
            notificationService.sendSubscriptionOnHoldNotification(user, subscription);
            log.debug("Async subscription on hold notification sent to user {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to send async subscription on hold notification to user {}: {}", user.getId(), e.getMessage());
        }
    }
}
