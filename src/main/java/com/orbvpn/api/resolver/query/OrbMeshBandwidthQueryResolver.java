package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.entity.BandwidthAddon;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.service.BandwidthAddonService;
import com.orbvpn.api.service.OrbMeshBandwidthValidationService;
import com.orbvpn.api.service.OrbMeshBandwidthValidationService.BandwidthStatus;
import com.orbvpn.api.service.OrbMeshBandwidthValidationService.ProtocolBandwidthUsage;
import com.orbvpn.api.service.UserService;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;

import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GraphQL Query Resolver for bandwidth status and usage.
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class OrbMeshBandwidthQueryResolver {

    private final OrbMeshBandwidthValidationService bandwidthService;
    private final BandwidthAddonService addonService;
    private final UserService userService;

    /**
     * Get current user's bandwidth status.
     */
    @Secured(USER)
    @QueryMapping
    public Map<String, Object> orbmeshBandwidthStatus() {
        User currentUser = userService.getUser();
        BandwidthStatus status = bandwidthService.getBandwidthStatus(currentUser);

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

        log.debug("User {} bandwidth status: used {} GB / {} GB",
                currentUser.getEmail(), status.getUsedGB(),
                status.isUnlimited() ? "unlimited" : status.getQuotaGB());

        return response;
    }

    /**
     * Get per-protocol bandwidth usage.
     */
    @Secured(USER)
    @QueryMapping
    public Map<String, Object> orbmeshProtocolBandwidthUsage(
            @Argument String from,
            @Argument String to) {
        User currentUser = userService.getUser();

        // Default to last 30 days
        LocalDateTime fromDate = from != null
                ? LocalDateTime.parse(from, DateTimeFormatter.ISO_DATE_TIME)
                : LocalDateTime.now().minusDays(30);
        LocalDateTime toDate = to != null
                ? LocalDateTime.parse(to, DateTimeFormatter.ISO_DATE_TIME)
                : LocalDateTime.now();

        ProtocolBandwidthUsage usage = bandwidthService.getProtocolBandwidthUsage(
                currentUser, fromDate, toDate);

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
     * Get available bandwidth addon products.
     */
    @Secured(USER)
    @QueryMapping
    public List<Map<String, Object>> orbmeshBandwidthProducts() {
        var products = addonService.getAvailableProducts();
        List<Map<String, Object>> result = new ArrayList<>();

        for (var product : products.values()) {
            Map<String, Object> p = new HashMap<>();
            p.put("productId", product.getProductId());
            p.put("name", product.getName());
            p.put("gigabytes", product.getGigabytes());
            p.put("priceUsd", product.getPriceUsd());
            result.add(p);
        }

        return result;
    }

    /**
     * Get user's bandwidth addon purchase history.
     */
    @Secured(USER)
    @QueryMapping
    public List<Map<String, Object>> myBandwidthAddons() {
        User currentUser = userService.getUser();
        List<BandwidthAddon> addons = addonService.getUserAddons(currentUser);
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
}
