package com.orbvpn.api.service.notification;

import com.orbvpn.api.config.LocaleConfig;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserSubscription;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocaleResolverService {

    private static final Map<String, Locale> SUPPORTED_LOCALES = new HashMap<>();

    static {
        // Predefined Locale constants
        SUPPORTED_LOCALES.put("EN", Locale.ENGLISH);
        SUPPORTED_LOCALES.put("FR", Locale.FRENCH);
        SUPPORTED_LOCALES.put("DE", Locale.GERMAN);
        SUPPORTED_LOCALES.put("IT", Locale.ITALIAN);
        SUPPORTED_LOCALES.put("JA", Locale.JAPANESE);
        SUPPORTED_LOCALES.put("KO", Locale.KOREAN);
        SUPPORTED_LOCALES.put("ZH-CN", Locale.SIMPLIFIED_CHINESE);
        SUPPORTED_LOCALES.put("ZH-TW", Locale.TRADITIONAL_CHINESE);
        SUPPORTED_LOCALES.put("ZH", Locale.CHINESE);
        SUPPORTED_LOCALES.put("US", Locale.US);
        SUPPORTED_LOCALES.put("UK", Locale.UK);
        SUPPORTED_LOCALES.put("CA", Locale.CANADA);
        SUPPORTED_LOCALES.put("CA-FR", Locale.CANADA_FRENCH);
        SUPPORTED_LOCALES.put("CHINA", Locale.CHINA);
        SUPPORTED_LOCALES.put("CN", Locale.CHINA);
        SUPPORTED_LOCALES.put("PRC", Locale.PRC);
        SUPPORTED_LOCALES.put("FRANCE", Locale.FRANCE);
        SUPPORTED_LOCALES.put("GERMANY", Locale.GERMANY);
        SUPPORTED_LOCALES.put("ITALY", Locale.ITALY);
        SUPPORTED_LOCALES.put("JAPAN", Locale.JAPAN);
        SUPPORTED_LOCALES.put("KOREA", Locale.KOREA);
        SUPPORTED_LOCALES.put("TAIWAN", Locale.TAIWAN);

        // Locales instantiated with language codes
        SUPPORTED_LOCALES.put("AF", Locale.of("af")); // Afrikaans
        SUPPORTED_LOCALES.put("AR", Locale.of("ar")); // Arabic
        SUPPORTED_LOCALES.put("BG", Locale.of("bg")); // Bulgarian
        SUPPORTED_LOCALES.put("CS", Locale.of("cs")); // Czech
        SUPPORTED_LOCALES.put("DA", Locale.of("da")); // Danish
        SUPPORTED_LOCALES.put("FA", Locale.of("fa")); // Persian (Farsi)
        SUPPORTED_LOCALES.put("EL", Locale.of("el")); // Greek
        SUPPORTED_LOCALES.put("ES", Locale.of("es")); // Spanish
        SUPPORTED_LOCALES.put("ET", Locale.of("et")); // Estonian
        SUPPORTED_LOCALES.put("FI", Locale.of("fi")); // Finnish
        SUPPORTED_LOCALES.put("HE", Locale.of("he")); // Hebrew
        SUPPORTED_LOCALES.put("HI", Locale.of("hi")); // Hindi
        SUPPORTED_LOCALES.put("HR", Locale.of("hr")); // Croatian
        SUPPORTED_LOCALES.put("HU", Locale.of("hu")); // Hungarian
        SUPPORTED_LOCALES.put("ID", Locale.of("id")); // Indonesian
        SUPPORTED_LOCALES.put("IS", Locale.of("is")); // Icelandic
        SUPPORTED_LOCALES.put("LT", Locale.of("lt")); // Lithuanian
        SUPPORTED_LOCALES.put("LV", Locale.of("lv")); // Latvian
        SUPPORTED_LOCALES.put("NL", Locale.of("nl")); // Dutch
        SUPPORTED_LOCALES.put("NO", Locale.of("no")); // Norwegian
        SUPPORTED_LOCALES.put("PL", Locale.of("pl")); // Polish
        SUPPORTED_LOCALES.put("PT", Locale.of("pt")); // Portuguese
        SUPPORTED_LOCALES.put("RO", Locale.of("ro")); // Romanian
        SUPPORTED_LOCALES.put("RU", Locale.of("ru")); // Russian
        SUPPORTED_LOCALES.put("SK", Locale.of("sk")); // Slovak
        SUPPORTED_LOCALES.put("SL", Locale.of("sl")); // Slovenian
        SUPPORTED_LOCALES.put("SV", Locale.of("sv")); // Swedish
        SUPPORTED_LOCALES.put("TR", Locale.of("tr")); // Turkish
        SUPPORTED_LOCALES.put("VI", Locale.of("vi")); // Vietnamese
    }

    public Locale resolveUserLocale(User user) {
        log.debug("Resolving locale for user ID: {}", user.getId());

        // Step 1: Check user profile language
        if (user.getProfile() != null) {
            log.debug("User profile found");
            if (user.getProfile().getLanguage() != null) {
                log.debug("Profile language found: {}", user.getProfile().getLanguage());
                return user.getProfile().getLanguage();
            } else {
                log.debug("Profile exists but language is null");
            }
        } else {
            log.debug("User profile is null");
        }

        // Step 2: Check service group language
        UserSubscription currentSubscription = user.getCurrentSubscription();
        if (currentSubscription == null) {
            log.debug("User has no current subscription");
        } else {
            log.debug("Current subscription found, ID: {}", currentSubscription.getId());
            if (currentSubscription.getGroup() == null) {
                log.debug("Subscription group is null");
            } else {
                log.debug("Subscription group found, ID: {}", currentSubscription.getGroup().getId());
                if (currentSubscription.getGroup().getServiceGroup() == null) {
                    log.debug("Service group is null");
                } else {
                    log.debug("Service group found, ID: {}", currentSubscription.getGroup().getServiceGroup().getId());
                    if (currentSubscription.getGroup().getServiceGroup().getLanguage() == null) {
                        log.debug("Service group language is null");
                    } else {
                        Locale groupLocale = currentSubscription.getGroup().getServiceGroup().getLanguage();
                        log.debug("Using service group language: {}", groupLocale);
                        return groupLocale;
                    }
                }
            }
        }

        log.debug("Falling back to default locale: {}", LocaleConfig.DEFAULT_LOCALE);
        return LocaleConfig.DEFAULT_LOCALE;
    }

    public boolean isSupported(Locale locale) {
        return locale != null && SUPPORTED_LOCALES.containsKey(locale.getLanguage().toUpperCase());
    }

    public Locale resolveLocale(String languageCode) {
        if (languageCode == null) {
            return LocaleConfig.DEFAULT_LOCALE;
        }

        return SUPPORTED_LOCALES.getOrDefault(
                languageCode.toUpperCase(),
                LocaleConfig.DEFAULT_LOCALE);
    }

    public List<String> getSupportedLanguageCodes() {
        return Arrays.asList(SUPPORTED_LOCALES.keySet().toArray(new String[0]));
    }
}