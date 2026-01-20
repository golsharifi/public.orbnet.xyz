package com.orbvpn.api.service;

import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.exception.BandwidthExceededException;
import com.orbvpn.api.exception.SubscriptionExpiredException;
import com.orbvpn.api.exception.DeviceLimitExceededException;
import com.orbvpn.api.repository.OrbMeshConnectionStatsRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Service for validating user subscription and device limits
 * before allowing VPN connections (WireGuard or VLESS).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrbMeshSubscriptionValidationService {

    private final OrbMeshConnectionStatsRepository connectionStatsRepository;

    /**
     * Validate that user has an active, non-expired subscription.
     *
     * @param user The user requesting connection
     * @throws SubscriptionExpiredException if no active subscription or subscription expired
     */
    public void validateSubscription(User user) {
        UserSubscription subscription = user.getCurrentSubscription();

        // Check if user has any subscription
        if (subscription == null) {
            log.warn("User {} has no subscription", user.getEmail());
            throw new SubscriptionExpiredException("No active subscription found. Please subscribe to continue.");
        }

        // Check if subscription is valid (includes status check)
        if (!subscription.isValid()) {
            log.warn("User {} subscription is not valid (status: {})",
                    user.getEmail(), subscription.getStatus());
            throw new SubscriptionExpiredException("Subscription is not active. Please renew your subscription.");
        }

        // Check if subscription is expired
        if (subscription.getExpiresAt() != null && subscription.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("User {} subscription expired at {}", user.getEmail(), subscription.getExpiresAt());
            throw new SubscriptionExpiredException("Subscription expired on " + subscription.getExpiresAt().toLocalDate() + ". Please renew to continue.");
        }

        log.debug("User {} subscription validated successfully (expires: {})",
                user.getEmail(), subscription.getExpiresAt());
    }

    /**
     * Validate that user hasn't exceeded their device/connection limit.
     *
     * @param user The user requesting connection
     * @throws DeviceLimitExceededException if device limit exceeded
     */
    public void validateDeviceLimit(User user) {
        UserSubscription subscription = user.getCurrentSubscription();

        if (subscription == null) {
            // No subscription - allow 0 connections
            throw new DeviceLimitExceededException("No active subscription. Device limit is 0.");
        }

        int deviceLimit = subscription.getMultiLoginCount();
        int activeConnections = connectionStatsRepository.countActiveConnectionsByUserId(user.getId());

        log.debug("User {} has {} active connections, limit is {}",
                user.getEmail(), activeConnections, deviceLimit);

        if (activeConnections >= deviceLimit) {
            log.warn("User {} exceeded device limit: {} active >= {} allowed",
                    user.getEmail(), activeConnections, deviceLimit);
            throw new DeviceLimitExceededException(
                    String.format("Device limit exceeded. You have %d active connections, maximum allowed is %d. " +
                            "Please disconnect another device or upgrade your plan.",
                            activeConnections, deviceLimit));
        }
    }

    /**
     * Validate that user hasn't exceeded their bandwidth quota.
     *
     * @param user The user requesting connection
     * @throws BandwidthExceededException if bandwidth limit exceeded
     */
    public void validateBandwidth(User user) {
        UserSubscription subscription = user.getCurrentSubscription();

        if (subscription == null) {
            return; // No subscription - will be caught by subscription validation
        }

        if (subscription.isBandwidthExceeded()) {
            Long used = subscription.getBandwidthUsedBytes();
            Long quota = subscription.getTotalBandwidthQuota();

            log.warn("User {} exceeded bandwidth limit: {} / {} bytes",
                    user.getEmail(), used, quota);

            double usedGB = used != null ? used / (1024.0 * 1024.0 * 1024.0) : 0;
            double quotaGB = quota != null ? quota / (1024.0 * 1024.0 * 1024.0) : 0;

            throw new BandwidthExceededException(
                    String.format("Bandwidth limit exceeded. You have used %.2f GB of your %.2f GB quota. " +
                            "Please purchase additional bandwidth or upgrade your plan.",
                            usedGB, quotaGB));
        }
    }

    /**
     * Validate subscription, device limit, and bandwidth.
     * Call this before creating a new VPN config/connection.
     *
     * @param user The user requesting connection
     * @throws SubscriptionExpiredException if subscription invalid
     * @throws DeviceLimitExceededException if device limit exceeded
     * @throws BandwidthExceededException if bandwidth limit exceeded
     */
    public void validateConnectionAllowed(User user) {
        validateSubscription(user);
        validateDeviceLimit(user);
        validateBandwidth(user);
    }

    /**
     * Get the number of remaining device slots for a user.
     *
     * @param user The user to check
     * @return Number of additional devices that can connect (0 or more)
     */
    public int getRemainingDeviceSlots(User user) {
        UserSubscription subscription = user.getCurrentSubscription();

        if (subscription == null) {
            return 0;
        }

        int deviceLimit = subscription.getMultiLoginCount();
        int activeConnections = connectionStatsRepository.countActiveConnectionsByUserId(user.getId());

        return Math.max(0, deviceLimit - activeConnections);
    }

    /**
     * Check if user can add one more connection without throwing exception.
     * Useful for UI to show connection availability.
     *
     * @param user The user to check
     * @return true if user can connect, false otherwise
     */
    public boolean canUserConnect(User user) {
        try {
            validateConnectionAllowed(user);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get detailed connection status for a user.
     *
     * @param user The user to check
     * @return ConnectionStatus with all relevant info
     */
    public ConnectionStatus getConnectionStatus(User user) {
        UserSubscription subscription = user.getCurrentSubscription();

        ConnectionStatus status = new ConnectionStatus();
        status.setHasSubscription(subscription != null);

        if (subscription == null) {
            status.setSubscriptionValid(false);
            status.setDeviceLimit(0);
            status.setActiveConnections(0);
            status.setCanConnect(false);
            status.setMessage("No active subscription");
            return status;
        }

        status.setSubscriptionValid(subscription.isValid());
        status.setSubscriptionExpiresAt(subscription.getExpiresAt());
        status.setDeviceLimit(subscription.getMultiLoginCount());
        status.setActiveConnections(connectionStatsRepository.countActiveConnectionsByUserId(user.getId()));
        status.setRemainingSlots(Math.max(0, status.getDeviceLimit() - status.getActiveConnections()));

        // Bandwidth info
        Long bandwidthQuota = subscription.getTotalBandwidthQuota();
        status.setHasBandwidthQuota(bandwidthQuota != null);
        status.setBandwidthUnlimited(bandwidthQuota == null);
        status.setBandwidthQuotaBytes(bandwidthQuota);
        status.setBandwidthUsedBytes(subscription.getBandwidthUsedBytes() != null
                ? subscription.getBandwidthUsedBytes() : 0L);
        status.setBandwidthRemainingBytes(subscription.getRemainingBandwidth());
        status.setBandwidthUsagePercent(subscription.getBandwidthUsagePercent());
        status.setBandwidthExceeded(subscription.isBandwidthExceeded());

        // Determine if user can connect (all checks must pass)
        boolean canConnect = status.isSubscriptionValid()
                && status.getRemainingSlots() > 0
                && !status.isBandwidthExceeded();
        status.setCanConnect(canConnect);

        if (!status.isSubscriptionValid()) {
            status.setMessage("Subscription expired or invalid");
        } else if (status.getRemainingSlots() <= 0) {
            status.setMessage("Device limit reached");
        } else if (status.isBandwidthExceeded()) {
            status.setMessage("Bandwidth limit exceeded");
        } else {
            status.setMessage("Ready to connect");
        }

        return status;
    }

    /**
     * Connection status DTO for detailed status reporting.
     */
    @lombok.Data
    public static class ConnectionStatus {
        private boolean hasSubscription;
        private boolean subscriptionValid;
        private LocalDateTime subscriptionExpiresAt;
        private int deviceLimit;
        private int activeConnections;
        private int remainingSlots;
        private boolean canConnect;
        private String message;

        // Bandwidth info
        private boolean hasBandwidthQuota;
        private boolean bandwidthUnlimited;
        private Long bandwidthQuotaBytes;
        private Long bandwidthUsedBytes;
        private Long bandwidthRemainingBytes;
        private double bandwidthUsagePercent;
        private boolean bandwidthExceeded;
    }
}
