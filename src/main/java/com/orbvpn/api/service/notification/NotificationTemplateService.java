package com.orbvpn.api.service.notification;

import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserProfile;
import com.orbvpn.api.domain.entity.UserSubscription;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class NotificationTemplateService {
    private static final Logger log = LoggerFactory.getLogger(NotificationTemplateService.class);

    private final MessageSource messageSource;

    // Template keys
    private static final String PREFIX = "notification.";
    public static final String BIRTHDAY_SMS = PREFIX + "birthday.sms";
    public static final String BIRTHDAY_EMAIL = PREFIX + "birthday.email";
    public static final String SUBSCRIPTION_EXPIRY = PREFIX + "subscription.expiry";
    public static final String WELCOME = PREFIX + "welcome";

    public String getLocalizedMessage(String key, Locale locale, Object... args) {
        try {
            return messageSource.getMessage(key, args, locale);
        } catch (Exception e) {
            // Fallback to default locale if translation is missing
            return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
        }
    }

    public Locale resolveUserLocale(User user) {
        log.debug("Resolving locale for user: {}", user.getEmail());

        if (user.getProfile() != null && user.getProfile().getLanguage() != null) {
            log.debug("Using profile language: {}", user.getProfile().getLanguage());
            return user.getProfile().getLanguage();
        }

        // Check service group language if user has an active subscription
        UserSubscription currentSubscription = user.getCurrentSubscription();
        if (currentSubscription != null &&
                currentSubscription.getGroup() != null &&
                currentSubscription.getGroup().getServiceGroup() != null &&
                currentSubscription.getGroup().getServiceGroup().getLanguage() != null) {
            log.debug("Using service group language: {}",
                    currentSubscription.getGroup().getServiceGroup().getLanguage());

            return currentSubscription.getGroup().getServiceGroup().getLanguage();
        }

        log.debug("Falling back to LocaleContextHolder: {}", LocaleContextHolder.getLocale());
        return LocaleContextHolder.getLocale();
    }

    public Locale resolveAdminLocale(User admin) {
        if (admin != null && admin.getProfile() != null &&
                admin.getProfile().getLanguage() != null) {
            return admin.getProfile().getLanguage();
        }
        return LocaleContextHolder.getLocale();
    }

    public NotificationContent getBirthdayNotification(UserProfile userProfile, Locale locale) {
        String name = userProfile.getFirstName() != null ? userProfile.getFirstName() : "";

        return NotificationContent.builder()
                .smsMessage(getLocalizedMessage(BIRTHDAY_SMS + ".text", locale, name))
                .emailTitle(getLocalizedMessage(BIRTHDAY_EMAIL + ".title", locale))
                .emailMessage(getLocalizedMessage(BIRTHDAY_EMAIL + ".body", locale, name))
                .build();
    }

    @lombok.Builder
    @lombok.Getter
    public static class NotificationContent {
        private String smsMessage;
        private String emailTitle;
        private String emailMessage;
    }
}