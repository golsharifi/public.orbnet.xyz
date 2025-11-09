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
        SUPPORTED_LOCALES.put("AF", new Locale("af")); // Afrikaans
        SUPPORTED_LOCALES.put("AR", new Locale("ar")); // Arabic
        SUPPORTED_LOCALES.put("BG", new Locale("bg")); // Bulgarian
        SUPPORTED_LOCALES.put("CS", new Locale("cs")); // Czech
        SUPPORTED_LOCALES.put("DA", new Locale("da")); // Danish
        SUPPORTED_LOCALES.put("FA", new Locale("fa")); // Persian (Farsi)
        SUPPORTED_LOCALES.put("EL", new Locale("el")); // Greek
        SUPPORTED_LOCALES.put("ES", new Locale("es")); // Spanish
        SUPPORTED_LOCALES.put("ET", new Locale("et")); // Estonian
        SUPPORTED_LOCALES.put("FI", new Locale("fi")); // Finnish
        SUPPORTED_LOCALES.put("HE", new Locale("he")); // Hebrew
        SUPPORTED_LOCALES.put("HI", new Locale("hi")); // Hindi
        SUPPORTED_LOCALES.put("HR", new Locale("hr")); // Croatian
        SUPPORTED_LOCALES.put("HU", new Locale("hu")); // Hungarian
        SUPPORTED_LOCALES.put("ID", new Locale("id")); // Indonesian
        SUPPORTED_LOCALES.put("IS", new Locale("is")); // Icelandic
        SUPPORTED_LOCALES.put("LT", new Locale("lt")); // Lithuanian
        SUPPORTED_LOCALES.put("LV", new Locale("lv")); // Latvian
        SUPPORTED_LOCALES.put("NL", new Locale("nl")); // Dutch
        SUPPORTED_LOCALES.put("NO", new Locale("no")); // Norwegian
        SUPPORTED_LOCALES.put("PL", new Locale("pl")); // Polish
        SUPPORTED_LOCALES.put("PT", new Locale("pt")); // Portuguese
        SUPPORTED_LOCALES.put("RO", new Locale("ro")); // Romanian
        SUPPORTED_LOCALES.put("RU", new Locale("ru")); // Russian
        SUPPORTED_LOCALES.put("SK", new Locale("sk")); // Slovak
        SUPPORTED_LOCALES.put("SL", new Locale("sl")); // Slovenian
        SUPPORTED_LOCALES.put("SV", new Locale("sv")); // Swedish
        SUPPORTED_LOCALES.put("TR", new Locale("tr")); // Turkish
        SUPPORTED_LOCALES.put("VI", new Locale("vi")); // Vietnamese
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