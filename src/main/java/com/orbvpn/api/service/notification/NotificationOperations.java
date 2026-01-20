package com.orbvpn.api.service.notification;

import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.domain.entity.GiftCard;
import com.orbvpn.api.domain.entity.UserExtraLogins;
import java.util.Locale;
import java.util.Map;

public interface NotificationOperations {
    // Change return type to void for consistency
    void sendBirthdayWish();

    void subscriptionExpirationReminder();

    void afterSubscriptionExpiredNotification();

    void welcomingNewUsersCreatedByAdmin(User user, UserSubscription subscription, String password);

    void sendSubscriptionRenewalNotification(User user, UserSubscription subscription);

    void resetPassword(User user, String token);

    void resetPasswordDone(User user);

    void sendAdminPasswordResetNotification(User user, String newPassword);

    void sendGiftCardRedemptionNotification(User user, GiftCard giftCard);

    void sendExtraLoginsConfirmation(User user, UserExtraLogins extraLogins);

    void sendExtraLoginsExpirationReminder(User user, UserExtraLogins extraLogins, int daysRemaining);

    void sendExtraLoginsExpiredNotification(User user, UserExtraLogins extraLogins);

    void sendExtraLoginsGiftNotification(User sender, User recipient, UserExtraLogins gift);

    void sendGiftCardCancellationNotification(GiftCard giftCard);

    void notifyUserPasswordReEncryption(User user, String newPassword);

    void welcomingNewUsersWithoutSubscription(User user, String password);

    void sendVerificationEmail(String toEmail, String verificationCode, Locale locale);

    void sendSuccessVerificationEmail(String toEmail, Locale locale);

    void sendSystemNotification(String toEmail, Map<String, Object> variables, Locale locale);

    void sendInvoiceEmail(String toEmail, Map<String, Object> variables, Locale locale);

    void sendTokenCodeToUser(User user, String token);

    void sendMagicLoginCode(User user, String code);

    void sendMagicLink(User user, String token);

    void sendTrialEndingNotification(User user, UserSubscription subscription, java.time.LocalDateTime trialEnd);

    void sendPaymentFailedNotification(User user, UserSubscription subscription);
}