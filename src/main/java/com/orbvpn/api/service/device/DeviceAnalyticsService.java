package com.orbvpn.api.service.device;

import com.orbvpn.api.domain.dto.DeviceUsageStats;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserDevice;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.repository.UserDeviceRepository;
import com.orbvpn.api.service.UserService;
import com.orbvpn.api.service.subscription.UserSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for device usage analytics and pattern analysis.
 * Provides insights into device usage, connection patterns, and potential issues.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DeviceAnalyticsService {

    private final UserDeviceRepository userDeviceRepository;
    private final UserSubscriptionService userSubscriptionService;
    private final UserService userService;

    // Thresholds for abuse detection
    private static final int MAX_DEVICE_CHANGES_PER_DAY = 10;
    private static final int MAX_DEVICE_CHANGES_PER_HOUR = 5;
    private static final int MAX_UNIQUE_DEVICES_PER_WEEK = 20;

    /**
     * Get device usage statistics for the current user.
     */
    public DeviceUsageStats getUserDeviceStats() {
        User user = userService.getUser();
        return getUserDeviceStats(user);
    }

    /**
     * Get device usage statistics for a specific user.
     */
    public DeviceUsageStats getUserDeviceStats(User user) {
        List<UserDevice> devices = userDeviceRepository.getUserDeviceByUser(user);
        UserSubscription subscription = userSubscriptionService.getCurrentSubscription(user);

        int allowedDevices = subscription != null ? subscription.getMultiLoginCount() : 0;
        long activeDevices = devices.stream()
                .filter(d -> Boolean.TRUE.equals(d.getIsActive()))
                .count();

        // Calculate device usage over time periods
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayAgo = now.minusDays(1);
        LocalDateTime weekAgo = now.minusWeeks(1);
        LocalDateTime monthAgo = now.minusMonths(1);

        long devicesLastDay = devices.stream()
                .filter(d -> d.getLoginDate() != null && d.getLoginDate().isAfter(dayAgo))
                .count();

        long devicesLastWeek = devices.stream()
                .filter(d -> d.getLoginDate() != null && d.getLoginDate().isAfter(weekAgo))
                .count();

        long devicesLastMonth = devices.stream()
                .filter(d -> d.getLoginDate() != null && d.getLoginDate().isAfter(monthAgo))
                .count();

        // Get unique device models
        Set<String> uniqueModels = devices.stream()
                .map(UserDevice::getDeviceModel)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Get unique OS types
        Set<String> uniqueOS = devices.stream()
                .map(UserDevice::getOs)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Calculate average session duration
        double avgSessionMinutes = calculateAverageSessionDuration(devices);

        // Detect suspicious patterns
        List<String> suspiciousPatterns = detectSuspiciousPatterns(devices, allowedDevices);

        return DeviceUsageStats.builder()
                .userId(user.getId())
                .userEmail(user.getEmail())
                .allowedDevices(allowedDevices)
                .activeDevices((int) activeDevices)
                .totalDevicesRegistered(devices.size())
                .devicesLastDay((int) devicesLastDay)
                .devicesLastWeek((int) devicesLastWeek)
                .devicesLastMonth((int) devicesLastMonth)
                .uniqueDeviceModels(uniqueModels.size())
                .uniqueOperatingSystems(uniqueOS.size())
                .averageSessionMinutes(avgSessionMinutes)
                .deviceModels(new ArrayList<>(uniqueModels))
                .operatingSystems(new ArrayList<>(uniqueOS))
                .suspiciousPatterns(suspiciousPatterns)
                .hasSuspiciousActivity(!suspiciousPatterns.isEmpty())
                .utilizationPercent(allowedDevices > 0
                        ? (activeDevices * 100.0 / allowedDevices) : 0)
                .build();
    }

    /**
     * Check if a user is exhibiting suspicious device usage patterns.
     */
    public boolean hasSuspiciousActivity(User user) {
        DeviceUsageStats stats = getUserDeviceStats(user);
        return stats.isHasSuspiciousActivity();
    }

    /**
     * Get device usage summary for reseller's users.
     */
    public List<DeviceUsageStats> getResellerUsersDeviceStats(int resellerId, int limit) {
        // This would be implemented with a custom query to get reseller's users
        // For now, return empty list - can be enhanced later
        log.info("Getting device stats for reseller {} with limit {}", resellerId, limit);
        return new ArrayList<>();
    }

    /**
     * Calculate average session duration from device records.
     */
    private double calculateAverageSessionDuration(List<UserDevice> devices) {
        List<Long> sessionMinutes = devices.stream()
                .filter(d -> d.getLoginDate() != null && d.getLogoutDate() != null)
                .map(d -> ChronoUnit.MINUTES.between(d.getLoginDate(), d.getLogoutDate()))
                .filter(m -> m > 0 && m < 1440) // Valid sessions less than 24 hours
                .collect(Collectors.toList());

        if (sessionMinutes.isEmpty()) {
            return 0;
        }

        return sessionMinutes.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);
    }

    /**
     * Detect suspicious patterns in device usage.
     */
    private List<String> detectSuspiciousPatterns(List<UserDevice> devices, int allowedDevices) {
        List<String> patterns = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        // Check for too many device changes in last hour
        long changesLastHour = devices.stream()
                .filter(d -> d.getLoginDate() != null)
                .filter(d -> ChronoUnit.HOURS.between(d.getLoginDate(), now) < 1)
                .count();
        if (changesLastHour > MAX_DEVICE_CHANGES_PER_HOUR) {
            patterns.add("EXCESSIVE_DEVICE_CHANGES_HOURLY: " + changesLastHour + " devices in last hour");
        }

        // Check for too many device changes in last day
        long changesLastDay = devices.stream()
                .filter(d -> d.getLoginDate() != null)
                .filter(d -> ChronoUnit.DAYS.between(d.getLoginDate(), now) < 1)
                .count();
        if (changesLastDay > MAX_DEVICE_CHANGES_PER_DAY) {
            patterns.add("EXCESSIVE_DEVICE_CHANGES_DAILY: " + changesLastDay + " devices in last 24 hours");
        }

        // Check for too many unique devices in last week
        Set<String> uniqueDevicesWeek = devices.stream()
                .filter(d -> d.getLoginDate() != null)
                .filter(d -> ChronoUnit.WEEKS.between(d.getLoginDate(), now) < 1)
                .map(UserDevice::getDeviceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (uniqueDevicesWeek.size() > MAX_UNIQUE_DEVICES_PER_WEEK) {
            patterns.add("EXCESSIVE_UNIQUE_DEVICES: " + uniqueDevicesWeek.size() + " unique devices in last week");
        }

        // Check for potential account sharing (devices from many different platforms)
        Set<String> uniqueOS = devices.stream()
                .filter(d -> d.getLoginDate() != null)
                .filter(d -> ChronoUnit.DAYS.between(d.getLoginDate(), now) < 7)
                .map(UserDevice::getOs)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (uniqueOS.size() > 3) {
            patterns.add("POSSIBLE_ACCOUNT_SHARING: " + uniqueOS.size() + " different OS types detected");
        }

        // Check if usage exceeds allowed devices significantly
        long activeDevices = devices.stream()
                .filter(d -> Boolean.TRUE.equals(d.getIsActive()))
                .count();
        if (allowedDevices > 0 && activeDevices > allowedDevices * 1.5) {
            patterns.add("DEVICE_LIMIT_EXCEEDED: " + activeDevices + " active vs " + allowedDevices + " allowed");
        }

        // Check for rapid device switching (same user switching devices very quickly)
        List<UserDevice> recentDevices = devices.stream()
                .filter(d -> d.getLoginDate() != null)
                .filter(d -> ChronoUnit.HOURS.between(d.getLoginDate(), now) < 1)
                .sorted(Comparator.comparing(UserDevice::getLoginDate))
                .collect(Collectors.toList());

        if (recentDevices.size() > 2) {
            for (int i = 1; i < recentDevices.size(); i++) {
                long minutesBetween = ChronoUnit.MINUTES.between(
                        recentDevices.get(i - 1).getLoginDate(),
                        recentDevices.get(i).getLoginDate());
                if (minutesBetween < 1) {
                    patterns.add("RAPID_DEVICE_SWITCHING: Less than 1 minute between device changes");
                    break;
                }
            }
        }

        return patterns;
    }

    /**
     * Get aggregated device statistics for admin dashboard.
     */
    public Map<String, Object> getSystemDeviceStats() {
        Map<String, Object> stats = new HashMap<>();

        // These would be populated with actual database queries
        // For now, return placeholder data
        stats.put("totalActiveDevices", 0);
        stats.put("totalUsers", 0);
        stats.put("avgDevicesPerUser", 0);
        stats.put("topDeviceModels", new ArrayList<>());
        stats.put("topOperatingSystems", new ArrayList<>());
        stats.put("usersWithSuspiciousActivity", 0);

        return stats;
    }

    /**
     * Record device usage event for analytics.
     */
    @Transactional
    public void recordDeviceEvent(User user, String deviceId, String eventType) {
        log.debug("Recording device event: user={}, device={}, event={}",
                user.getId(), deviceId, eventType);
        // This could be used to feed into a more sophisticated analytics system
        // For now, we rely on the existing UserDevice records
    }
}
