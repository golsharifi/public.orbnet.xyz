package com.orbvpn.api.service.notification;

import com.orbvpn.api.domain.entity.EmailUnsubscribeToken;
import com.orbvpn.api.domain.entity.NotificationPreferences;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.enums.NotificationCategory;
import com.orbvpn.api.repository.EmailUnsubscribeTokenRepository;
import com.orbvpn.api.repository.NotificationPreferencesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for managing email unsubscribe operations.
 * Provides secure token generation, one-click unsubscribe, and preferences management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailUnsubscribeService {

    private final EmailUnsubscribeTokenRepository tokenRepository;
    private final NotificationPreferencesRepository preferencesRepository;

    @Value("${application.website-url:https://orbnet.xyz}")
    private String baseUrl;

    /**
     * Gets or creates an unsubscribe token for a user.
     * For global unsubscribe, category should be null.
     *
     * @param user The user to get/create token for
     * @param category Optional category for granular unsubscribe
     * @return The unsubscribe token string
     */
    @Transactional
    public String getOrCreateUnsubscribeToken(User user, NotificationCategory category) {
        Optional<EmailUnsubscribeToken> existingToken = category == null
                ? tokenRepository.findGlobalTokenByUser(user)
                : tokenRepository.findByUserAndCategoryAndUsedFalse(user, category);

        if (existingToken.isPresent() && existingToken.get().isValid()) {
            return existingToken.get().getToken();
        }

        // Create new token
        EmailUnsubscribeToken newToken = EmailUnsubscribeToken.createForUser(user, category);
        tokenRepository.save(newToken);
        log.debug("Created new unsubscribe token for user {} category {}",
                user.getId(), category);

        return newToken.getToken();
    }

    /**
     * Gets the global unsubscribe token for a user.
     */
    @Transactional
    public String getGlobalUnsubscribeToken(User user) {
        return getOrCreateUnsubscribeToken(user, null);
    }

    /**
     * Generates unsubscribe URL for email footer.
     *
     * @param user The user
     * @return The full unsubscribe URL
     */
    @Transactional
    public String generateUnsubscribeUrl(User user) {
        String token = getGlobalUnsubscribeToken(user);
        return baseUrl + "/api/email/unsubscribe?token=" + token;
    }

    /**
     * Generates preferences management URL for email footer.
     *
     * @param user The user
     * @return The full preferences URL
     */
    @Transactional
    public String generatePreferencesUrl(User user) {
        String token = getGlobalUnsubscribeToken(user);
        return baseUrl + "/email/preferences?token=" + token;
    }

    /**
     * Process one-click unsubscribe from email link.
     *
     * @param token The unsubscribe token
     * @param ipAddress The IP address of the request
     * @param userAgent The user agent of the request
     * @return UnsubscribeResult with details of the operation
     */
    @Transactional
    public UnsubscribeResult processUnsubscribe(String token, String ipAddress, String userAgent) {
        Optional<EmailUnsubscribeToken> tokenOpt = tokenRepository.findByToken(token);

        if (tokenOpt.isEmpty()) {
            log.warn("Invalid unsubscribe token: {}", token);
            return UnsubscribeResult.invalid("Invalid or expired unsubscribe link.");
        }

        EmailUnsubscribeToken unsubscribeToken = tokenOpt.get();

        if (unsubscribeToken.isExpired()) {
            log.warn("Expired unsubscribe token for user: {}", unsubscribeToken.getUser().getId());
            return UnsubscribeResult.expired("This unsubscribe link has expired.");
        }

        User user = unsubscribeToken.getUser();
        NotificationPreferences prefs = getOrCreatePreferences(user);

        if (unsubscribeToken.getCategory() != null) {
            // Category-specific unsubscribe
            prefs.disableCategory(unsubscribeToken.getCategory());
            log.info("User {} unsubscribed from category: {}",
                    user.getId(), unsubscribeToken.getCategory());
        } else {
            // Global email unsubscribe
            prefs.unsubscribeFromEmail();
            log.info("User {} unsubscribed from all emails", user.getId());
        }

        // Mark token as used
        unsubscribeToken.markAsUsed(ipAddress, userAgent);
        tokenRepository.save(unsubscribeToken);
        preferencesRepository.save(prefs);

        return UnsubscribeResult.success(
                user.getEmail(),
                unsubscribeToken.getCategory(),
                generatePreferencesUrl(user)
        );
    }

    /**
     * Resubscribe a user to emails.
     *
     * @param token The token used for authentication
     * @return True if successful
     */
    @Transactional
    public boolean processResubscribe(String token) {
        Optional<EmailUnsubscribeToken> tokenOpt = tokenRepository.findByToken(token);

        if (tokenOpt.isEmpty()) {
            return false;
        }

        User user = tokenOpt.get().getUser();
        NotificationPreferences prefs = getOrCreatePreferences(user);
        prefs.resubscribeToEmail();
        preferencesRepository.save(prefs);

        log.info("User {} resubscribed to emails", user.getId());
        return true;
    }

    /**
     * Get notification preferences for token (for preferences management page).
     *
     * @param token The unsubscribe token
     * @return The preferences or empty if invalid token
     */
    @Transactional(readOnly = true)
    public Optional<PreferencesView> getPreferencesForToken(String token) {
        return tokenRepository.findByToken(token)
                .map(t -> {
                    User user = t.getUser();
                    NotificationPreferences prefs = getOrCreatePreferences(user);
                    return new PreferencesView(
                            user.getEmail(),
                            prefs.isEmailUnsubscribed(),
                            prefs.getEnabledCategories(),
                            prefs.getEnabledChannels(),
                            token
                    );
                });
    }

    /**
     * Update preferences via token (for preferences management page).
     */
    @Transactional
    public boolean updatePreferencesViaToken(String token, Set<NotificationCategory> enabledCategories,
                                              boolean emailEnabled) {
        Optional<EmailUnsubscribeToken> tokenOpt = tokenRepository.findByToken(token);

        if (tokenOpt.isEmpty()) {
            return false;
        }

        User user = tokenOpt.get().getUser();
        NotificationPreferences prefs = getOrCreatePreferences(user);

        // Update email subscription status
        if (emailEnabled && prefs.isEmailUnsubscribed()) {
            prefs.resubscribeToEmail();
        } else if (!emailEnabled && !prefs.isEmailUnsubscribed()) {
            prefs.unsubscribeFromEmail();
        }

        // Update enabled categories
        prefs.setEnabledCategories(enabledCategories != null ? enabledCategories : new HashSet<>());

        preferencesRepository.save(prefs);
        log.info("Updated preferences for user {} via token", user.getId());
        return true;
    }

    /**
     * Admin: Get unsubscribe statistics.
     */
    @Transactional(readOnly = true)
    public UnsubscribeStats getUnsubscribeStats(int daysBack) {
        LocalDateTime since = LocalDateTime.now().minusDays(daysBack);

        long totalUnsubscribes = tokenRepository.countUnsubscribesSince(since);
        List<Object[]> byCategory = tokenRepository.countUnsubscribesByCategorySince(since);

        Map<NotificationCategory, Long> categoryCounts = new HashMap<>();
        long globalCount = 0;

        for (Object[] row : byCategory) {
            NotificationCategory cat = (NotificationCategory) row[0];
            Long count = (Long) row[1];
            if (cat == null) {
                globalCount = count;
            } else {
                categoryCounts.put(cat, count);
            }
        }

        return new UnsubscribeStats(totalUnsubscribes, globalCount, categoryCounts, daysBack);
    }

    /**
     * Admin: Get list of unsubscribed users.
     */
    @Transactional(readOnly = true)
    public List<UnsubscribedUserInfo> getUnsubscribedUsers() {
        return preferencesRepository.findAll().stream()
                .filter(NotificationPreferences::isEmailUnsubscribed)
                .map(p -> new UnsubscribedUserInfo(
                        p.getUser().getId(),
                        p.getUser().getEmail(),
                        p.getUnsubscribedAt()
                ))
                .toList();
    }

    /**
     * Cleanup expired tokens (runs daily).
     */
    @Scheduled(cron = "0 0 3 * * ?") // 3 AM daily
    @Transactional
    public void cleanupExpiredTokens() {
        int deleted = tokenRepository.deleteExpiredTokens(LocalDateTime.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired unsubscribe tokens", deleted);
        }
    }

    private NotificationPreferences getOrCreatePreferences(User user) {
        return preferencesRepository.findByUser(user)
                .orElseGet(() -> {
                    NotificationPreferences newPrefs = NotificationPreferences.createDefault(user);
                    return preferencesRepository.save(newPrefs);
                });
    }

    // DTO classes
    public record UnsubscribeResult(
            boolean success,
            String email,
            NotificationCategory category,
            String message,
            String preferencesUrl
    ) {
        public static UnsubscribeResult success(String email, NotificationCategory category, String preferencesUrl) {
            String message = category != null
                    ? "You have been unsubscribed from " + category.getDescription().toLowerCase() + " emails."
                    : "You have been unsubscribed from all marketing emails.";
            return new UnsubscribeResult(true, email, category, message, preferencesUrl);
        }

        public static UnsubscribeResult invalid(String message) {
            return new UnsubscribeResult(false, null, null, message, null);
        }

        public static UnsubscribeResult expired(String message) {
            return new UnsubscribeResult(false, null, null, message, null);
        }
    }

    public record PreferencesView(
            String email,
            boolean emailUnsubscribed,
            Set<NotificationCategory> enabledCategories,
            Set<com.orbvpn.api.domain.enums.NotificationChannel> enabledChannels,
            String token
    ) {}

    public record UnsubscribeStats(
            long totalUnsubscribes,
            long globalUnsubscribes,
            Map<NotificationCategory, Long> byCategoryCount,
            int periodDays
    ) {}

    public record UnsubscribedUserInfo(
            Integer userId,
            String email,
            LocalDateTime unsubscribedAt
    ) {}
}
