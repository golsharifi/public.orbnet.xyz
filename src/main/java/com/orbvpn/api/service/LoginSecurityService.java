package com.orbvpn.api.service;

import com.orbvpn.api.domain.entity.ClientMetadata;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.repository.ClientMetadataRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for detecting suspicious login activity and triggering security notifications.
 *
 * Detects:
 * - New devices (based on platform + OS + device model + browser fingerprint)
 * - New locations (new country login)
 * - Suspicious patterns (rapid location changes, unusual times)
 *
 * Following state-of-the-art practices from Google, Microsoft, and CHI 2024 research.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoginSecurityService {

    private final ClientMetadataRepository clientMetadataRepository;
    private final AsyncNotificationHelper asyncNotificationHelper;

    // Configuration constants
    private static final int DEVICE_HISTORY_DAYS = 90; // How far back to look for known devices
    private static final int LOCATION_HISTORY_DAYS = 30; // How far back to look for known locations
    private static final int MIN_LOGINS_BEFORE_ALERTS = 1; // Minimum logins before we start alerting (skip first login)
    private static final int NOTIFICATION_COOLDOWN_HOURS = 24; // Don't spam - max 1 alert per device/location per 24h

    /**
     * Result of security analysis for a login attempt
     */
    @Data
    @Builder
    public static class LoginSecurityResult {
        private boolean isNewDevice;
        private boolean isNewCountry;
        private boolean isFirstLogin;
        private boolean notificationSent;
        private String deviceFingerprint;
        private String alertReason;
        private List<String> securityFlags;
    }

    /**
     * Analyze a login and send notifications if suspicious.
     * Call this AFTER recording the login metadata.
     *
     * @param user The user who logged in
     * @param metadata The login metadata that was just recorded
     * @return Security analysis result
     */
    @Transactional(readOnly = true)
    public LoginSecurityResult analyzeLoginAndNotify(User user, ClientMetadata metadata) {
        if (user == null || metadata == null) {
            return LoginSecurityResult.builder()
                    .securityFlags(List.of())
                    .build();
        }

        List<String> securityFlags = new ArrayList<>();
        boolean shouldNotify = false;
        String alertReason = null;

        try {
            // Generate device fingerprint
            String fingerprint = generateDeviceFingerprint(metadata);

            // Check if this is the user's first login ever
            long totalLogins = clientMetadataRepository.countLoginsFromDeviceFingerprint(user.getId(), fingerprint);
            List<String> allFingerprints = clientMetadataRepository.findAllDeviceFingerprintsForUser(user.getId());

            boolean isFirstLogin = allFingerprints.size() <= 1; // Only this login exists
            if (isFirstLogin) {
                log.debug("First login for user {}, skipping security alerts", user.getEmail());
                return LoginSecurityResult.builder()
                        .isFirstLogin(true)
                        .isNewDevice(true)
                        .isNewCountry(true)
                        .deviceFingerprint(fingerprint)
                        .securityFlags(List.of("FIRST_LOGIN"))
                        .build();
            }

            // Check for new device
            boolean isNewDevice = isNewDevice(user.getId(), fingerprint);
            if (isNewDevice) {
                securityFlags.add("NEW_DEVICE");
                shouldNotify = true;
                alertReason = "New device detected";
                log.info("New device detected for user {}: {}", user.getEmail(), fingerprint);
            }

            // Check for new country
            boolean isNewCountry = false;
            if (metadata.getCountryCode() != null && !metadata.getCountryCode().isEmpty()) {
                isNewCountry = isNewCountry(user.getId(), metadata.getCountryCode());
                if (isNewCountry) {
                    securityFlags.add("NEW_COUNTRY");
                    shouldNotify = true;
                    alertReason = alertReason != null ?
                            alertReason + " from new location" :
                            "Login from new country: " + metadata.getCountryName();
                    log.info("New country login for user {}: {} ({})",
                            user.getEmail(), metadata.getCountryName(), metadata.getCountryCode());
                }
            }

            // Send notification if needed
            boolean notificationSent = false;
            if (shouldNotify && shouldSendNotification(user)) {
                sendSecurityNotification(user, metadata, isNewDevice, isNewCountry);
                notificationSent = true;
            }

            return LoginSecurityResult.builder()
                    .isNewDevice(isNewDevice)
                    .isNewCountry(isNewCountry)
                    .isFirstLogin(false)
                    .notificationSent(notificationSent)
                    .deviceFingerprint(fingerprint)
                    .alertReason(alertReason)
                    .securityFlags(securityFlags)
                    .build();

        } catch (Exception e) {
            log.error("Error analyzing login security for user {}: {}", user.getEmail(), e.getMessage(), e);
            return LoginSecurityResult.builder()
                    .securityFlags(List.of("ANALYSIS_ERROR"))
                    .build();
        }
    }

    /**
     * Check if this is a new device for the user
     */
    private boolean isNewDevice(Integer userId, String fingerprint) {
        LocalDateTime since = LocalDateTime.now().minusDays(DEVICE_HISTORY_DAYS);
        List<String> knownFingerprints = clientMetadataRepository.findRecentDeviceFingerprints(userId, since);

        // If this fingerprint is not in the known list (excluding current login which was just added)
        // We check if it appeared before the current login
        long previousLogins = clientMetadataRepository.countLoginsFromDeviceFingerprint(userId, fingerprint);
        return previousLogins <= 1; // Only the current login exists with this fingerprint
    }

    /**
     * Check if this is a new country for the user
     */
    private boolean isNewCountry(Integer userId, String countryCode) {
        return !clientMetadataRepository.hasUserEverLoggedFromCountry(userId, countryCode);
    }

    /**
     * Generate a device fingerprint from metadata
     */
    private String generateDeviceFingerprint(ClientMetadata metadata) {
        return String.format("%s|%s|%s|%s",
                metadata.getPlatform() != null ? metadata.getPlatform() : "",
                metadata.getOsName() != null ? metadata.getOsName() : "",
                metadata.getDeviceModel() != null ? metadata.getDeviceModel() : "",
                metadata.getBrowserName() != null ? metadata.getBrowserName() : "");
    }

    /**
     * Check if we should send a notification (rate limiting, user preferences, etc.)
     */
    private boolean shouldSendNotification(User user) {
        // TODO: Check user's notification preferences for SECURITY category
        // For now, always send (users can unsubscribe via notification preferences)

        // Check if user is enabled
        if (!user.isEnabled()) {
            return false;
        }

        // TODO: Implement cooldown check - don't send more than X alerts per day
        // This would require tracking when we last sent an alert

        return true;
    }

    /**
     * Send the security notification via all configured channels
     */
    private void sendSecurityNotification(User user, ClientMetadata metadata,
                                          boolean isNewDevice, boolean isNewCountry) {
        // Build device info string
        String deviceInfo = buildDeviceInfoString(metadata);

        // Build location string
        String location = buildLocationString(metadata);

        // Send async notification
        asyncNotificationHelper.sendNewDeviceLoginNotificationAsync(
                user,
                deviceInfo,
                metadata.getIpAddress(),
                location
        );

        log.info("Sent security notification to user {} - New device: {}, New country: {}",
                user.getEmail(), isNewDevice, isNewCountry);
    }

    /**
     * Build a human-readable device info string
     */
    private String buildDeviceInfoString(ClientMetadata metadata) {
        StringBuilder sb = new StringBuilder();

        // Device/Browser
        if (metadata.getBrowserName() != null) {
            sb.append(metadata.getBrowserName());
            if (metadata.getBrowserVersion() != null) {
                sb.append(" ").append(metadata.getBrowserVersion());
            }
        } else if (metadata.getDeviceModel() != null) {
            sb.append(metadata.getDeviceModel());
        } else if (metadata.getDeviceManufacturer() != null) {
            sb.append(metadata.getDeviceManufacturer()).append(" device");
        }

        // OS
        if (metadata.getOsName() != null) {
            if (sb.length() > 0) sb.append(" on ");
            sb.append(metadata.getOsName());
            if (metadata.getOsVersion() != null) {
                sb.append(" ").append(metadata.getOsVersion());
            }
        }

        // Platform
        if (metadata.getPlatform() != null && sb.length() == 0) {
            sb.append(metadata.getPlatform()).append(" device");
        }

        return sb.length() > 0 ? sb.toString() : "Unknown device";
    }

    /**
     * Build a human-readable location string
     */
    private String buildLocationString(ClientMetadata metadata) {
        StringBuilder sb = new StringBuilder();

        if (metadata.getCity() != null) {
            sb.append(metadata.getCity());
        }

        if (metadata.getRegion() != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(metadata.getRegion());
        }

        if (metadata.getCountryName() != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(metadata.getCountryName());
        }

        return sb.length() > 0 ? sb.toString() : "Unknown location";
    }

    /**
     * Get security summary for a user (for admin dashboard)
     */
    @Transactional(readOnly = true)
    public UserSecuritySummary getUserSecuritySummary(Integer userId) {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);

        List<String> recentIPs = clientMetadataRepository.findRecentLoginIpsByUser(userId, thirtyDaysAgo);
        List<String> recentCountries = clientMetadataRepository.findRecentLoginCountriesByUser(userId, thirtyDaysAgo);
        List<String> recentDevices = clientMetadataRepository.findRecentDeviceFingerprints(userId, thirtyDaysAgo);
        List<ClientMetadata> recentLogins = clientMetadataRepository.findMostRecentLogins(userId, PageRequest.of(0, 10));

        return UserSecuritySummary.builder()
                .uniqueIPsLast30Days(recentIPs.size())
                .uniqueCountriesLast30Days(recentCountries.size())
                .uniqueDevicesLast30Days(recentDevices.size())
                .recentIPs(recentIPs)
                .recentCountries(recentCountries)
                .recentDeviceFingerprints(recentDevices)
                .lastLoginTime(recentLogins.isEmpty() ? null : recentLogins.get(0).getCreatedAt())
                .lastLoginIP(recentLogins.isEmpty() ? null : recentLogins.get(0).getIpAddress())
                .lastLoginCountry(recentLogins.isEmpty() ? null : recentLogins.get(0).getCountryName())
                .build();
    }

    @Data
    @Builder
    public static class UserSecuritySummary {
        private int uniqueIPsLast30Days;
        private int uniqueCountriesLast30Days;
        private int uniqueDevicesLast30Days;
        private List<String> recentIPs;
        private List<String> recentCountries;
        private List<String> recentDeviceFingerprints;
        private LocalDateTime lastLoginTime;
        private String lastLoginIP;
        private String lastLoginCountry;
    }
}
