package com.orbvpn.api.service;

import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.domain.enums.WebhookEventType;
import com.orbvpn.api.repository.RadAcctRepository;
import com.orbvpn.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for enforcing bandwidth limits on user subscriptions.
 * Monitors usage and triggers webhooks when limits are exceeded or approaching.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BandwidthEnforcementService {

    private final UserRepository userRepository;
    private final RadAcctRepository radAcctRepository;
    private final AsyncNotificationHelper asyncNotificationHelper;

    // Warning threshold percentage (warn at 80% of limit)
    private static final double WARNING_THRESHOLD = 0.8;

    // Bytes in a GB
    private static final BigInteger BYTES_PER_GB = BigInteger.valueOf(1024L * 1024 * 1024);

    /**
     * Check bandwidth usage for a specific user.
     * Returns the enforcement result.
     */
    @Transactional(readOnly = true)
    public BandwidthCheckResult checkUserBandwidth(Integer userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("User not found for bandwidth check: {}", userId);
            return BandwidthCheckResult.notFound();
        }

        UserSubscription subscription = user.getCurrentSubscription();
        if (subscription == null) {
            log.debug("No active subscription for user: {}", userId);
            return BandwidthCheckResult.noSubscription();
        }

        String username = user.getUsername();

        // Get daily bandwidth usage
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        BigInteger dailyUsage = getDailyBandwidthUsage(username, startOfDay, now);
        BigInteger dailyLimit = subscription.getDailyBandwidth();

        // Get total bandwidth usage (since subscription start)
        BigInteger totalUsage = getTotalBandwidthUsage(username);
        BigInteger totalLimit = subscription.getDownloadUpload();

        BandwidthCheckResult result = BandwidthCheckResult.builder()
            .userId(userId)
            .username(username)
            .dailyUsageBytes(dailyUsage)
            .dailyLimitBytes(dailyLimit)
            .totalUsageBytes(totalUsage)
            .totalLimitBytes(totalLimit)
            .build();

        // Check if daily limit exceeded
        if (dailyLimit != null && dailyLimit.compareTo(BigInteger.ZERO) > 0) {
            result.setDailyLimitExceeded(dailyUsage.compareTo(dailyLimit) >= 0);
            result.setDailyWarning(dailyUsage.doubleValue() / dailyLimit.doubleValue() >= WARNING_THRESHOLD);
        }

        // Check if total limit exceeded
        if (totalLimit != null && totalLimit.compareTo(BigInteger.ZERO) > 0) {
            result.setTotalLimitExceeded(totalUsage.compareTo(totalLimit) >= 0);
            result.setTotalWarning(totalUsage.doubleValue() / totalLimit.doubleValue() >= WARNING_THRESHOLD);
        }

        return result;
    }

    /**
     * Enforce bandwidth limits for a user.
     * If limits are exceeded, takes appropriate action.
     */
    @Transactional
    public void enforceUserBandwidth(Integer userId) {
        BandwidthCheckResult result = checkUserBandwidth(userId);

        if (result.isNotFound() || result.isNoSubscription()) {
            return;
        }

        Map<String, Object> webhookPayload = new HashMap<>();
        webhookPayload.put("userId", userId);
        webhookPayload.put("username", result.getUsername());
        webhookPayload.put("dailyUsageGB", bytesToGB(result.getDailyUsageBytes()));
        webhookPayload.put("dailyLimitGB", bytesToGB(result.getDailyLimitBytes()));
        webhookPayload.put("totalUsageGB", bytesToGB(result.getTotalUsageBytes()));
        webhookPayload.put("totalLimitGB", bytesToGB(result.getTotalLimitBytes()));

        // Get user for notifications
        User user = userRepository.findById(userId).orElse(null);

        // Handle daily limit exceeded
        if (result.isDailyLimitExceeded()) {
            log.warn("Daily bandwidth limit exceeded for user {}: {} / {}",
                result.getUsername(),
                bytesToGB(result.getDailyUsageBytes()),
                bytesToGB(result.getDailyLimitBytes()));

            webhookPayload.put("limitType", "DAILY");
            asyncNotificationHelper.sendWebhookAsync(
                WebhookEventType.DAILY_LIMIT_REACHED.getEventName(),
                webhookPayload
            );

            // Send bandwidth exceeded notification to user
            if (user != null) {
                asyncNotificationHelper.sendBandwidthExceededNotificationAsync(user, "DAILY");
            }

            // Note: Actual disconnection would be handled by RADIUS server
            // based on Acct-Input-Octets / Acct-Output-Octets attributes
        }
        // Handle daily limit warning
        else if (result.isDailyWarning()) {
            double percentUsed = result.getDailyUsageBytes().doubleValue() / result.getDailyLimitBytes().doubleValue() * 100;
            log.info("Daily bandwidth warning for user {}: {}% used",
                result.getUsername(),
                (int) percentUsed);

            webhookPayload.put("limitType", "DAILY");
            webhookPayload.put("percentUsed", percentUsed);
            asyncNotificationHelper.sendWebhookAsync(
                WebhookEventType.BANDWIDTH_WARNING.getEventName(),
                webhookPayload
            );

            // Send bandwidth warning notification to user
            if (user != null) {
                asyncNotificationHelper.sendBandwidthWarningNotificationAsync(user, percentUsed, "DAILY");
            }
        }

        // Handle total limit exceeded
        if (result.isTotalLimitExceeded()) {
            log.warn("Total bandwidth limit exceeded for user {}: {} / {}",
                result.getUsername(),
                bytesToGB(result.getTotalUsageBytes()),
                bytesToGB(result.getTotalLimitBytes()));

            webhookPayload.put("limitType", "TOTAL");
            asyncNotificationHelper.sendWebhookAsync(
                WebhookEventType.BANDWIDTH_EXCEEDED.getEventName(),
                webhookPayload
            );

            // Send bandwidth exceeded notification to user
            if (user != null) {
                asyncNotificationHelper.sendBandwidthExceededNotificationAsync(user, "TOTAL");
            }
        }
        // Handle total limit warning
        else if (result.isTotalWarning()) {
            double percentUsed = result.getTotalUsageBytes().doubleValue() / result.getTotalLimitBytes().doubleValue() * 100;
            log.info("Total bandwidth warning for user {}: {}% used",
                result.getUsername(),
                (int) percentUsed);

            webhookPayload.put("limitType", "TOTAL");
            webhookPayload.put("percentUsed", percentUsed);
            asyncNotificationHelper.sendWebhookAsync(
                WebhookEventType.BANDWIDTH_WARNING.getEventName(),
                webhookPayload
            );

            // Send bandwidth warning notification to user
            if (user != null) {
                asyncNotificationHelper.sendBandwidthWarningNotificationAsync(user, percentUsed, "TOTAL");
            }
        }
    }

    /**
     * Check bandwidth usage for all active users.
     * Runs every 15 minutes.
     */
    @Scheduled(fixedRate = 900000) // Every 15 minutes
    @Transactional
    public void checkAllUsersBandwidth() {
        log.info("Starting bandwidth check for all active users");
        try {
            List<User> activeUsers = userRepository.findAllByActiveTrue();
            int exceededCount = 0;
            int warningCount = 0;

            for (User user : activeUsers) {
                if (user.getCurrentSubscription() == null) {
                    continue;
                }

                BandwidthCheckResult result = checkUserBandwidth(user.getId());

                if (result.isDailyLimitExceeded() || result.isTotalLimitExceeded()) {
                    exceededCount++;
                    enforceUserBandwidth(user.getId());
                } else if (result.isDailyWarning() || result.isTotalWarning()) {
                    warningCount++;
                    enforceUserBandwidth(user.getId());
                }
            }

            log.info("Bandwidth check completed: {} users exceeded limits, {} users at warning level",
                exceededCount, warningCount);
        } catch (Exception e) {
            log.error("Error during bandwidth check", e);
        }
    }

    /**
     * Get daily bandwidth usage for a user from RADIUS accounting.
     */
    private BigInteger getDailyBandwidthUsage(String username, LocalDateTime start, LocalDateTime end) {
        try {
            // Sum input and output octets from radacct for today
            Long inputOctets = radAcctRepository.sumInputOctets(username, start, end);
            Long outputOctets = radAcctRepository.sumOutputOctets(username, start, end);

            BigInteger input = inputOctets != null ? BigInteger.valueOf(inputOctets) : BigInteger.ZERO;
            BigInteger output = outputOctets != null ? BigInteger.valueOf(outputOctets) : BigInteger.ZERO;

            return input.add(output);
        } catch (Exception e) {
            log.error("Error getting daily bandwidth for user {}: {}", username, e.getMessage());
            return BigInteger.ZERO;
        }
    }

    /**
     * Get total bandwidth usage for a user from RADIUS accounting.
     */
    private BigInteger getTotalBandwidthUsage(String username) {
        try {
            Long inputOctets = radAcctRepository.sumTotalInputOctets(username);
            Long outputOctets = radAcctRepository.sumTotalOutputOctets(username);

            BigInteger input = inputOctets != null ? BigInteger.valueOf(inputOctets) : BigInteger.ZERO;
            BigInteger output = outputOctets != null ? BigInteger.valueOf(outputOctets) : BigInteger.ZERO;

            return input.add(output);
        } catch (Exception e) {
            log.error("Error getting total bandwidth for user {}: {}", username, e.getMessage());
            return BigInteger.ZERO;
        }
    }

    /**
     * Convert bytes to gigabytes for display.
     */
    private double bytesToGB(BigInteger bytes) {
        if (bytes == null) {
            return 0.0;
        }
        return bytes.doubleValue() / BYTES_PER_GB.doubleValue();
    }

    /**
     * Result of a bandwidth check.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class BandwidthCheckResult {
        private Integer userId;
        private String username;
        private BigInteger dailyUsageBytes;
        private BigInteger dailyLimitBytes;
        private BigInteger totalUsageBytes;
        private BigInteger totalLimitBytes;
        private boolean dailyLimitExceeded;
        private boolean dailyWarning;
        private boolean totalLimitExceeded;
        private boolean totalWarning;
        private boolean notFound;
        private boolean noSubscription;

        public static BandwidthCheckResult notFound() {
            return BandwidthCheckResult.builder().notFound(true).build();
        }

        public static BandwidthCheckResult noSubscription() {
            return BandwidthCheckResult.builder().noSubscription(true).build();
        }
    }
}
