package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.entity.OrbMeshConnectionStats;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.exception.NotFoundException;
import com.orbvpn.api.service.UserService;
import com.orbvpn.api.repository.OrbMeshConnectionStatsRepository;
import com.orbvpn.api.repository.UserSubscriptionRepository;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin GraphQL Mutation Resolver for bandwidth management.
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class OrbMeshBandwidthAdminMutationResolver {

    private static final long GB_IN_BYTES = 1024L * 1024L * 1024L;

    private final UserService userService;
    private final UserSubscriptionRepository subscriptionRepository;
    private final OrbMeshConnectionStatsRepository connectionStatsRepository;

    /**
     * Admin: Reset a user's bandwidth usage to zero.
     */
    @Secured(ADMIN)
    @MutationMapping
    @Transactional
    public Map<String, Object> adminResetUserBandwidth(
            @Argument Integer userId,
            @Argument String notes) {
        log.info("Admin resetting bandwidth for user ID: {}, notes: {}", userId, notes);

        Map<String, Object> response = new HashMap<>();

        try {
            User user = userService.getUserById(userId);

            UserSubscription subscription = user.getCurrentSubscription();
            if (subscription == null) {
                response.put("success", false);
                response.put("message", "User has no active subscription");
                response.put("userId", userId);
                response.put("email", user.getEmail());
                return response;
            }

            Long previousUsedBytes = subscription.getBandwidthUsedBytes();

            // Reset bandwidth usage
            subscription.setBandwidthUsedBytes(0L);
            subscriptionRepository.save(subscription);

            log.info("Reset bandwidth for user {}: {} bytes -> 0", user.getEmail(), previousUsedBytes);

            response.put("success", true);
            response.put("message", "Bandwidth usage reset successfully");
            response.put("previousUsedBytes", previousUsedBytes != null ? previousUsedBytes : 0L);
            response.put("userId", userId);
            response.put("email", user.getEmail());

        } catch (NotFoundException e) {
            response.put("success", false);
            response.put("message", "User not found: " + userId);
        }

        return response;
    }

    /**
     * Admin: Set a user's bandwidth quota.
     */
    @Secured(ADMIN)
    @MutationMapping
    @Transactional
    public Map<String, Object> adminSetUserBandwidthQuota(
            @Argument Integer userId,
            @Argument Double quotaGB,
            @Argument Boolean unlimited) {
        log.info("Admin setting bandwidth quota for user ID: {}, quotaGB: {}, unlimited: {}",
                userId, quotaGB, unlimited);

        Map<String, Object> response = new HashMap<>();

        try {
            User user = userService.getUserById(userId);

            UserSubscription subscription = user.getCurrentSubscription();
            if (subscription == null) {
                response.put("success", false);
                response.put("message", "User has no active subscription");
                response.put("userId", userId);
                response.put("email", user.getEmail());
                return response;
            }

            if (Boolean.TRUE.equals(unlimited)) {
                // Set unlimited bandwidth
                subscription.setBandwidthQuotaBytes(null);
                subscriptionRepository.save(subscription);

                log.info("Set unlimited bandwidth for user {}", user.getEmail());

                response.put("success", true);
                response.put("message", "Bandwidth set to unlimited");
                response.put("userId", userId);
                response.put("email", user.getEmail());
                response.put("newQuotaBytes", null);
                response.put("unlimited", true);
            } else if (quotaGB != null && quotaGB > 0) {
                // Set specific quota
                long quotaBytes = (long) (quotaGB * GB_IN_BYTES);
                subscription.setBandwidthQuotaBytes(quotaBytes);
                subscriptionRepository.save(subscription);

                log.info("Set bandwidth quota for user {}: {} GB ({} bytes)",
                        user.getEmail(), quotaGB, quotaBytes);

                response.put("success", true);
                response.put("message", String.format("Bandwidth quota set to %.2f GB", quotaGB));
                response.put("userId", userId);
                response.put("email", user.getEmail());
                response.put("newQuotaBytes", quotaBytes);
                response.put("unlimited", false);
            } else {
                response.put("success", false);
                response.put("message", "Must specify either quotaGB > 0 or unlimited = true");
                response.put("userId", userId);
                response.put("email", user.getEmail());
            }

        } catch (NotFoundException e) {
            response.put("success", false);
            response.put("message", "User not found: " + userId);
        }

        return response;
    }

    /**
     * Admin: Force disconnect all active connections for a user.
     */
    @Secured(ADMIN)
    @MutationMapping
    @Transactional
    public Map<String, Object> adminForceDisconnectUser(
            @Argument Integer userId,
            @Argument String reason) {
        log.info("Admin force disconnecting user ID: {}, reason: {}", userId, reason);

        Map<String, Object> response = new HashMap<>();

        try {
            User user = userService.getUserById(userId);

            // Find all active connections for this user
            List<OrbMeshConnectionStats> activeConnections =
                    connectionStatsRepository.findActiveConnectionsByUserId(userId);

            if (activeConnections.isEmpty()) {
                response.put("success", true);
                response.put("message", "User has no active connections");
                response.put("userId", userId);
                response.put("email", user.getEmail());
                response.put("disconnectedSessions", 0);
                return response;
            }

            // Mark all connections as disconnected
            LocalDateTime now = LocalDateTime.now();
            for (OrbMeshConnectionStats conn : activeConnections) {
                conn.setDisconnectedAt(now);
                conn.setDisconnectReason("admin_force_disconnect: " + reason);
                connectionStatsRepository.save(conn);
                log.debug("Disconnected session {} for user {}", conn.getSessionId(), user.getEmail());
            }

            log.info("Force disconnected {} sessions for user {}", activeConnections.size(), user.getEmail());

            response.put("success", true);
            response.put("message", String.format("Disconnected %d active sessions", activeConnections.size()));
            response.put("userId", userId);
            response.put("email", user.getEmail());
            response.put("disconnectedSessions", activeConnections.size());

        } catch (NotFoundException e) {
            response.put("success", false);
            response.put("message", "User not found: " + userId);
        }

        return response;
    }

}
