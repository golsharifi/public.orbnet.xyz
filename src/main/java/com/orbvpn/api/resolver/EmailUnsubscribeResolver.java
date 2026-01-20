package com.orbvpn.api.resolver;

import com.orbvpn.api.domain.entity.NotificationPreferences;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.enums.NotificationCategory;
import com.orbvpn.api.repository.NotificationPreferencesRepository;
import com.orbvpn.api.repository.UserRepository;
import com.orbvpn.api.service.UserService;
import com.orbvpn.api.service.notification.EmailUnsubscribeService;
import com.orbvpn.api.service.notification.EmailUnsubscribeService.UnsubscribeStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;

import java.util.*;
import java.util.stream.Collectors;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

/**
 * GraphQL resolver for email unsubscribe operations.
 * Provides both admin and user-facing operations.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class EmailUnsubscribeResolver {

    private final EmailUnsubscribeService unsubscribeService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final NotificationPreferencesRepository preferencesRepository;

    // ===== Admin Queries =====

    @QueryMapping
    @Secured(ADMIN)
    public Map<String, Object> getUnsubscribeStats(@Argument Integer daysBack) {
        int days = daysBack != null ? daysBack : 30;
        UnsubscribeStats stats = unsubscribeService.getUnsubscribeStats(days);

        Map<String, Object> result = new HashMap<>();
        result.put("totalUnsubscribes", stats.totalUnsubscribes());
        result.put("globalUnsubscribes", stats.globalUnsubscribes());
        result.put("periodDays", stats.periodDays());

        List<Map<String, Object>> byCategoryCount = stats.byCategoryCount().entrySet().stream()
                .map(e -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("category", e.getKey());
                    item.put("count", e.getValue());
                    return item;
                })
                .collect(Collectors.toList());
        result.put("byCategoryCount", byCategoryCount);

        return result;
    }

    @QueryMapping
    @Secured(ADMIN)
    public List<Map<String, Object>> getUnsubscribedUsers() {
        return unsubscribeService.getUnsubscribedUsers().stream()
                .map(u -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("userId", u.userId());
                    item.put("email", u.email());
                    item.put("unsubscribedAt", u.unsubscribedAt());
                    return item;
                })
                .collect(Collectors.toList());
    }

    @QueryMapping
    @Secured(ADMIN)
    public Map<String, Object> getUserEmailPreferences(@Argument Long userId) {
        User user = userRepository.findById(userId.intValue())
                .orElseThrow(() -> new RuntimeException("User not found"));

        NotificationPreferences prefs = getOrCreatePreferences(user);

        Map<String, Object> result = new HashMap<>();
        result.put("emailUnsubscribed", prefs.isEmailUnsubscribed());
        result.put("enabledCategories", prefs.getEnabledCategories());
        result.put("unsubscribedAt", prefs.getUnsubscribedAt());
        result.put("resubscribedAt", prefs.getResubscribedAt());

        return result;
    }

    // ===== Admin Mutations =====

    @MutationMapping
    @Secured(ADMIN)
    public boolean unsubscribeUserFromEmail(@Argument Long userId) {
        User user = userRepository.findById(userId.intValue())
                .orElseThrow(() -> new RuntimeException("User not found"));

        NotificationPreferences prefs = getOrCreatePreferences(user);
        prefs.unsubscribeFromEmail();
        preferencesRepository.save(prefs);

        log.info("Admin unsubscribed user {} from emails", userId);
        return true;
    }

    @MutationMapping
    @Secured(ADMIN)
    public boolean resubscribeUserToEmail(@Argument Long userId) {
        User user = userRepository.findById(userId.intValue())
                .orElseThrow(() -> new RuntimeException("User not found"));

        NotificationPreferences prefs = getOrCreatePreferences(user);
        prefs.resubscribeToEmail();
        preferencesRepository.save(prefs);

        log.info("Admin resubscribed user {} to emails", userId);
        return true;
    }

    @MutationMapping
    @Secured(ADMIN)
    public boolean updateUserEmailCategories(@Argument Long userId,
                                              @Argument List<NotificationCategory> categories) {
        User user = userRepository.findById(userId.intValue())
                .orElseThrow(() -> new RuntimeException("User not found"));

        NotificationPreferences prefs = getOrCreatePreferences(user);
        prefs.setEnabledCategories(new HashSet<>(categories));
        preferencesRepository.save(prefs);

        log.info("Admin updated email categories for user {}", userId);
        return true;
    }

    // ===== User Self-Service Mutations =====

    @MutationMapping
    public boolean unsubscribeFromEmail() {
        User user = userService.getUser();

        NotificationPreferences prefs = getOrCreatePreferences(user);
        prefs.unsubscribeFromEmail();
        preferencesRepository.save(prefs);

        log.info("User {} unsubscribed from emails", user.getId());
        return true;
    }

    @MutationMapping
    public boolean resubscribeToEmail() {
        User user = userService.getUser();

        NotificationPreferences prefs = getOrCreatePreferences(user);
        prefs.resubscribeToEmail();
        preferencesRepository.save(prefs);

        log.info("User {} resubscribed to emails", user.getId());
        return true;
    }

    @MutationMapping
    public NotificationPreferences updateEmailCategories(@Argument List<NotificationCategory> categories) {
        User user = userService.getUser();

        NotificationPreferences prefs = getOrCreatePreferences(user);
        prefs.setEnabledCategories(new HashSet<>(categories));
        preferencesRepository.save(prefs);

        log.info("User {} updated email categories", user.getId());
        return prefs;
    }

    private NotificationPreferences getOrCreatePreferences(User user) {
        return preferencesRepository.findByUser(user)
                .orElseGet(() -> {
                    NotificationPreferences newPrefs = NotificationPreferences.createDefault(user);
                    return preferencesRepository.save(newPrefs);
                });
    }
}
