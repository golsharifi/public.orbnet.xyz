package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.entity.BandwidthAddon;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.service.BandwidthAddonService;
import com.orbvpn.api.service.OrbMeshBandwidthValidationService;
import com.orbvpn.api.service.OrbMeshBandwidthValidationService.BandwidthStatus;
import com.orbvpn.api.service.OrbMeshBandwidthValidationService.ProtocolBandwidthUsage;
import com.orbvpn.api.service.UserService;
import com.orbvpn.api.repository.OrbMeshConnectionStatsRepository;
import com.orbvpn.api.repository.UserSubscriptionRepository;

import org.springframework.data.domain.Page;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin GraphQL Query Resolver for bandwidth management.
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class OrbMeshBandwidthAdminQueryResolver {

    private final OrbMeshBandwidthValidationService bandwidthService;
    private final BandwidthAddonService addonService;
    private final UserService userService;
    private final UserSubscriptionRepository subscriptionRepository;
    private final OrbMeshConnectionStatsRepository connectionStatsRepository;

    /**
     * Admin: Get specific user's bandwidth status.
     */
    @Secured(ADMIN)
    @QueryMapping
    public Map<String, Object> adminGetUserBandwidthStatus(@Argument Integer userId) {
        log.info("Admin fetching bandwidth status for user ID: {}", userId);

        User user = userService.getUserById(userId);
        BandwidthStatus status = bandwidthService.getBandwidthStatus(user);

        Map<String, Object> response = new HashMap<>();
        response.put("hasQuota", status.isHasQuota());
        response.put("unlimited", status.isUnlimited());
        response.put("quotaBytes", status.getQuotaBytes());
        response.put("usedBytes", status.getUsedBytes());
        response.put("remainingBytes", status.getRemainingBytes());
        response.put("addonBytes", status.getAddonBytes());
        response.put("usagePercent", status.getUsagePercent());
        response.put("exceeded", status.isExceeded());
        response.put("warning", status.isWarning());
        response.put("resetDate", status.getResetDate() != null ? status.getResetDate().toString() : null);
        response.put("message", status.getMessage());
        response.put("quotaGB", status.getQuotaGB());
        response.put("usedGB", status.getUsedGB());
        response.put("remainingGB", status.getRemainingGB());
        response.put("addonGB", status.getAddonGB());

        return response;
    }

    /**
     * Admin: Get specific user's bandwidth usage by protocol.
     */
    @Secured(ADMIN)
    @QueryMapping
    public Map<String, Object> adminGetUserProtocolBandwidth(
            @Argument Integer userId,
            @Argument String from,
            @Argument String to) {
        log.info("Admin fetching protocol bandwidth for user ID: {}", userId);

        User user = userService.getUserById(userId);

        LocalDateTime fromDate = from != null
                ? LocalDateTime.parse(from, DateTimeFormatter.ISO_DATE_TIME)
                : LocalDateTime.now().minusDays(30);
        LocalDateTime toDate = to != null
                ? LocalDateTime.parse(to, DateTimeFormatter.ISO_DATE_TIME)
                : LocalDateTime.now();

        ProtocolBandwidthUsage usage = bandwidthService.getProtocolBandwidthUsage(user, fromDate, toDate);

        Map<String, Object> response = new HashMap<>();
        response.put("wireguardBytes", usage.getWireguardBytes());
        response.put("vlessBytes", usage.getVlessBytes());
        response.put("totalBytes", usage.getTotalBytes());
        response.put("wireguardGB", usage.getWireguardGB());
        response.put("vlessGB", usage.getVlessGB());
        response.put("totalGB", usage.getTotalGB());

        return response;
    }

    /**
     * Admin: Get specific user's bandwidth addon history.
     */
    @Secured(ADMIN)
    @QueryMapping
    public List<Map<String, Object>> adminGetUserBandwidthAddons(@Argument Integer userId) {
        log.info("Admin fetching bandwidth addons for user ID: {}", userId);

        User user = userService.getUserById(userId);
        List<BandwidthAddon> addons = addonService.getUserAddons(user);
        return convertAddonsToResponse(addons);
    }

    /**
     * Admin: List all bandwidth addon purchases (paginated).
     */
    @Secured(ADMIN)
    @QueryMapping
    public Map<String, Object> adminAllBandwidthAddons(@Argument Integer page, @Argument Integer size) {
        log.info("Admin fetching all bandwidth addons - page: {}, size: {}", page, size);

        Page<BandwidthAddon> addonPage = addonService.getAllAddonsPaginated(page, size);

        Map<String, Object> response = new HashMap<>();
        response.put("content", convertAddonsToResponse(addonPage.getContent()));
        response.put("totalElements", addonPage.getTotalElements());
        response.put("totalPages", addonPage.getTotalPages());
        response.put("number", addonPage.getNumber());
        response.put("size", addonPage.getSize());

        return response;
    }

    /**
     * Admin: Get bandwidth usage report.
     */
    @Secured(ADMIN)
    @QueryMapping
    public Map<String, Object> adminBandwidthUsageReport(@Argument String from, @Argument String to) {
        log.info("Admin fetching bandwidth usage report");

        LocalDateTime fromDate = from != null
                ? LocalDateTime.parse(from, DateTimeFormatter.ISO_DATE_TIME)
                : LocalDateTime.now().minusDays(30);
        LocalDateTime toDate = to != null
                ? LocalDateTime.parse(to, DateTimeFormatter.ISO_DATE_TIME)
                : LocalDateTime.now();

        // Get statistics from subscriptions
        long usersWithQuota = subscriptionRepository.countByBandwidthQuotaBytesNotNull();
        long usersUnlimited = subscriptionRepository.countByBandwidthQuotaBytesNull();

        // Get exceeded and warning counts
        List<UserSubscription> exceededSubs = subscriptionRepository.findUsersExceedingBandwidth();
        List<UserSubscription> warningSubs = subscriptionRepository.findUsersNearBandwidthLimit(80.0);

        // Get total bandwidth used
        Long totalUsed = connectionStatsRepository.sumTotalBandwidthInPeriod(fromDate, toDate);
        Long wireguardUsed = connectionStatsRepository.sumBandwidthByVpnProtocolInPeriod("wireguard", fromDate, toDate);
        Long vlessUsed = connectionStatsRepository.sumBandwidthByVpnProtocolInPeriod("vless", fromDate, toDate);

        // Get addon stats
        long totalAddonsPurchased = addonService.getTotalAddonCount();
        double totalAddonsRevenue = addonService.getTotalRevenue().doubleValue();

        // Build top users by usage
        List<Map<String, Object>> topUsers = new ArrayList<>();
        for (UserSubscription sub : exceededSubs.subList(0, Math.min(10, exceededSubs.size()))) {
            topUsers.add(buildUserBandwidthSummary(sub));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("totalUsersWithQuota", (int) usersWithQuota);
        response.put("totalUsersUnlimited", (int) usersUnlimited);
        response.put("totalUsersExceeded", exceededSubs.size());
        response.put("totalUsersWarning", warningSubs.size());
        response.put("totalBandwidthUsedBytes", totalUsed != null ? totalUsed : 0L);
        response.put("totalBandwidthQuotaBytes", 0L); // TODO: sum all quotas
        response.put("totalAddonsPurchased", (int) totalAddonsPurchased);
        response.put("totalAddonsRevenue", totalAddonsRevenue);
        response.put("wireguardBandwidthBytes", wireguardUsed != null ? wireguardUsed : 0L);
        response.put("vlessBandwidthBytes", vlessUsed != null ? vlessUsed : 0L);
        response.put("topUsersByUsage", topUsers);
        response.put("periodStart", fromDate.toString());
        response.put("periodEnd", toDate.toString());

        return response;
    }

    /**
     * Admin: Get users with exceeded bandwidth.
     */
    @Secured(ADMIN)
    @QueryMapping
    public Map<String, Object> adminUsersExceededBandwidth(@Argument Integer page, @Argument Integer size) {
        log.info("Admin fetching users with exceeded bandwidth - page: {}, size: {}", page, size);

        List<UserSubscription> exceededSubs = subscriptionRepository.findUsersExceedingBandwidth();

        // Manual pagination
        int start = page * size;
        int end = Math.min(start + size, exceededSubs.size());
        List<UserSubscription> pagedSubs = exceededSubs.subList(start, end);

        List<Map<String, Object>> content = new ArrayList<>();
        for (UserSubscription sub : pagedSubs) {
            content.add(buildUserBandwidthSummary(sub));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("content", content);
        response.put("totalElements", (long) exceededSubs.size());
        response.put("totalPages", (int) Math.ceil((double) exceededSubs.size() / size));
        response.put("number", page);
        response.put("size", size);

        return response;
    }

    /**
     * Admin: Get users near bandwidth limit (>80% usage).
     */
    @Secured(ADMIN)
    @QueryMapping
    public Map<String, Object> adminUsersNearBandwidthLimit(@Argument Integer page, @Argument Integer size) {
        log.info("Admin fetching users near bandwidth limit - page: {}, size: {}", page, size);

        List<UserSubscription> warningSubs = subscriptionRepository.findUsersNearBandwidthLimit(80.0);

        // Manual pagination
        int start = page * size;
        int end = Math.min(start + size, warningSubs.size());
        List<UserSubscription> pagedSubs = warningSubs.subList(start, end);

        List<Map<String, Object>> content = new ArrayList<>();
        for (UserSubscription sub : pagedSubs) {
            content.add(buildUserBandwidthSummary(sub));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("content", content);
        response.put("totalElements", (long) warningSubs.size());
        response.put("totalPages", (int) Math.ceil((double) warningSubs.size() / size));
        response.put("number", page);
        response.put("size", size);

        return response;
    }

    // Helper methods

    private List<Map<String, Object>> convertAddonsToResponse(List<BandwidthAddon> addons) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (BandwidthAddon addon : addons) {
            Map<String, Object> a = new HashMap<>();
            a.put("id", addon.getId());
            a.put("productId", addon.getProductId());
            a.put("bandwidthBytes", addon.getBandwidthBytes());
            a.put("bandwidthGB", addon.getBandwidthGB());
            a.put("price", addon.getPrice());
            a.put("currency", addon.getCurrency());
            a.put("gateway", addon.getGateway() != null ? addon.getGateway().name() : null);
            a.put("applied", addon.getApplied());
            a.put("appliedAt", addon.getAppliedAt() != null ? addon.getAppliedAt().toString() : null);
            a.put("isPromotional", addon.getIsPromotional());
            a.put("createdAt", addon.getCreatedAt().toString());
            result.add(a);
        }
        return result;
    }

    private Map<String, Object> buildUserBandwidthSummary(UserSubscription subscription) {
        User user = subscription.getUser();
        Long quotaBytes = subscription.getTotalBandwidthQuota();
        Long usedBytes = subscription.getBandwidthUsedBytes() != null ? subscription.getBandwidthUsedBytes() : 0L;
        Long addonBytes = subscription.getBandwidthAddonBytes() != null ? subscription.getBandwidthAddonBytes() : 0L;
        double usagePercent = subscription.getBandwidthUsagePercent();
        boolean exceeded = subscription.isBandwidthExceeded();
        boolean unlimited = quotaBytes == null;

        Map<String, Object> summary = new HashMap<>();
        summary.put("userId", user.getId());
        summary.put("email", user.getEmail());
        summary.put("username", user.getUsername());
        summary.put("quotaBytes", quotaBytes);
        summary.put("usedBytes", usedBytes);
        summary.put("addonBytes", addonBytes);
        summary.put("usagePercent", usagePercent);
        summary.put("exceeded", exceeded);
        summary.put("warning", usagePercent >= 80.0 && usagePercent < 100.0);
        summary.put("unlimited", unlimited);
        summary.put("subscriptionPlan", subscription.getGroup() != null ? subscription.getGroup().getName() : null);
        summary.put("lastActivity", null); // TODO: get from connection stats

        return summary;
    }
}
