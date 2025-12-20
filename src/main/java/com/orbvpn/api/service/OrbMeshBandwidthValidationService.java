package com.orbvpn.api.service;

import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.exception.BandwidthExceededException;
import com.orbvpn.api.repository.OrbMeshConnectionStatsRepository;
import com.orbvpn.api.repository.UserSubscriptionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Service for validating and tracking bandwidth usage.
 * Enforces bandwidth limits before allowing VPN connections.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrbMeshBandwidthValidationService {

    private final OrbMeshConnectionStatsRepository connectionStatsRepository;
    private final UserSubscriptionRepository subscriptionRepository;

    // Warning threshold - warn at 80% usage
    private static final double WARNING_THRESHOLD = 0.8;

    // GB in bytes
    private static final long BYTES_PER_GB = 1024L * 1024L * 1024L;

    /**
     * Validate that user has remaining bandwidth quota.
     *
     * @param user The user requesting connection
     * @throws BandwidthExceededException if bandwidth limit exceeded
     */
    public void validateBandwidth(User user) {
        UserSubscription subscription = user.getCurrentSubscription();

        if (subscription == null) {
            // No subscription - might be handled by subscription validation
            return;
        }

        // Check if bandwidth limit is set and exceeded
        if (subscription.isBandwidthExceeded()) {
            Long used = subscription.getBandwidthUsedBytes();
            Long quota = subscription.getTotalBandwidthQuota();

            log.warn("User {} exceeded bandwidth limit: {} / {} GB",
                    user.getEmail(),
                    bytesToGB(used),
                    bytesToGB(quota));

            throw new BandwidthExceededException(
                    String.format("Bandwidth limit exceeded. You have used %.2f GB of your %.2f GB quota. " +
                            "Please purchase additional bandwidth or upgrade your plan.",
                            bytesToGB(used), bytesToGB(quota)));
        }

        // Log warning if approaching limit
        double usagePercent = subscription.getBandwidthUsagePercent();
        if (usagePercent >= WARNING_THRESHOLD * 100) {
            log.info("User {} is at {:.1f}% of bandwidth quota",
                    user.getEmail(), usagePercent);
        }
    }

    /**
     * Record bandwidth usage for a user.
     * Called when a connection ends.
     *
     * @param userId The user ID
     * @param bytesSent Bytes sent during session
     * @param bytesReceived Bytes received during session
     */
    @Transactional
    public void recordBandwidthUsage(Integer userId, long bytesSent, long bytesReceived) {
        long totalBytes = bytesSent + bytesReceived;

        subscriptionRepository.findCurrentSubscription(userId).ifPresent(subscription -> {
            subscription.addBandwidthUsage(totalBytes);
            subscriptionRepository.save(subscription);

            log.debug("Recorded {} bytes bandwidth usage for user {}, total now: {} GB",
                    totalBytes, userId, bytesToGB(subscription.getBandwidthUsedBytes()));
        });
    }

    /**
     * Get bandwidth status for a user.
     */
    public BandwidthStatus getBandwidthStatus(User user) {
        UserSubscription subscription = user.getCurrentSubscription();

        BandwidthStatus status = new BandwidthStatus();

        if (subscription == null) {
            status.setHasQuota(false);
            status.setUnlimited(true);
            status.setMessage("No active subscription");
            return status;
        }

        Long quota = subscription.getTotalBandwidthQuota();
        Long used = subscription.getBandwidthUsedBytes();
        Long remaining = subscription.getRemainingBandwidth();

        status.setHasQuota(quota != null);
        status.setUnlimited(quota == null);
        status.setQuotaBytes(quota);
        status.setUsedBytes(used != null ? used : 0L);
        status.setRemainingBytes(remaining);
        status.setUsagePercent(subscription.getBandwidthUsagePercent());
        status.setExceeded(subscription.isBandwidthExceeded());
        status.setWarning(status.getUsagePercent() >= WARNING_THRESHOLD * 100);
        status.setAddonBytes(subscription.getBandwidthAddonBytes());
        status.setResetDate(subscription.getBandwidthResetDate());

        // Formatted values for display
        status.setQuotaGB(bytesToGB(quota));
        status.setUsedGB(bytesToGB(used));
        status.setRemainingGB(bytesToGB(remaining));
        status.setAddonGB(bytesToGB(subscription.getBandwidthAddonBytes()));

        if (status.isUnlimited()) {
            status.setMessage("Unlimited bandwidth");
        } else if (status.isExceeded()) {
            status.setMessage("Bandwidth limit exceeded");
        } else if (status.isWarning()) {
            status.setMessage(String.format("%.0f%% of bandwidth used", status.getUsagePercent()));
        } else {
            status.setMessage(String.format("%.2f GB remaining", status.getRemainingGB()));
        }

        return status;
    }

    /**
     * Get per-protocol bandwidth usage for a user.
     */
    @Transactional(readOnly = true)
    public ProtocolBandwidthUsage getProtocolBandwidthUsage(User user, LocalDateTime from, LocalDateTime to) {
        ProtocolBandwidthUsage usage = new ProtocolBandwidthUsage();

        // Get WireGuard usage
        Long wireguardBytes = connectionStatsRepository.sumBandwidthByUserAndProtocol(
                user.getId(), "wireguard", from, to);
        usage.setWireguardBytes(wireguardBytes != null ? wireguardBytes : 0L);

        // Get VLESS usage
        Long vlessBytes = connectionStatsRepository.sumBandwidthByUserAndProtocol(
                user.getId(), "vless", from, to);
        usage.setVlessBytes(vlessBytes != null ? vlessBytes : 0L);

        // Total
        usage.setTotalBytes(usage.getWireguardBytes() + usage.getVlessBytes());

        // Formatted values
        usage.setWireguardGB(bytesToGB(usage.getWireguardBytes()));
        usage.setVlessGB(bytesToGB(usage.getVlessBytes()));
        usage.setTotalGB(bytesToGB(usage.getTotalBytes()));

        return usage;
    }

    /**
     * Reset monthly bandwidth usage for a subscription.
     * Called by scheduler when reset date is reached.
     */
    @Transactional
    public void resetMonthlyBandwidth(UserSubscription subscription) {
        subscription.setBandwidthUsedBytes(0L);
        subscription.setBandwidthResetDate(LocalDateTime.now().plusMonths(1));
        subscriptionRepository.save(subscription);

        log.info("Reset monthly bandwidth for subscription {}", subscription.getId());
    }

    private double bytesToGB(Long bytes) {
        if (bytes == null) {
            return 0.0;
        }
        return bytes / (double) BYTES_PER_GB;
    }

    /**
     * Bandwidth status DTO.
     */
    @lombok.Data
    public static class BandwidthStatus {
        private boolean hasQuota;
        private boolean unlimited;
        private Long quotaBytes;
        private Long usedBytes;
        private Long remainingBytes;
        private Long addonBytes;
        private double usagePercent;
        private boolean exceeded;
        private boolean warning;
        private LocalDateTime resetDate;
        private String message;

        // Formatted values in GB
        private double quotaGB;
        private double usedGB;
        private double remainingGB;
        private double addonGB;
    }

    /**
     * Per-protocol bandwidth usage DTO.
     */
    @lombok.Data
    public static class ProtocolBandwidthUsage {
        private long wireguardBytes;
        private long vlessBytes;
        private long totalBytes;

        private double wireguardGB;
        private double vlessGB;
        private double totalGB;
    }
}
