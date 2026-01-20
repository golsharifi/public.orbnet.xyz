package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.entity.BandwidthAddon;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.enums.GatewayName;
import com.orbvpn.api.service.BandwidthAddonService;
import com.orbvpn.api.service.UserService;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;
import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * GraphQL Mutation Resolver for bandwidth addon purchases.
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class OrbMeshBandwidthMutationResolver {

    private final BandwidthAddonService addonService;
    private final UserService userService;

    // GB in bytes
    private static final long BYTES_PER_GB = 1024L * 1024L * 1024L;

    /**
     * Purchase bandwidth addon from app store.
     */
    @Secured(USER)
    @MutationMapping
    public Map<String, Object> purchaseBandwidthAddon(@Argument Map<String, Object> input) {
        Map<String, Object> result = new HashMap<>();

        try {
            User currentUser = userService.getUser();

            String productId = (String) input.get("productId");
            String purchaseToken = (String) input.get("purchaseToken");
            String orderId = (String) input.get("orderId");
            String gatewayStr = (String) input.get("gateway");
            Double priceVal = input.get("price") != null ? ((Number) input.get("price")).doubleValue() : null;
            String currency = (String) input.get("currency");

            GatewayName gateway = gatewayStr != null ? GatewayName.valueOf(gatewayStr.toUpperCase()) : null;
            BigDecimal price = priceVal != null ? BigDecimal.valueOf(priceVal) : null;

            log.info("User {} purchasing bandwidth addon: product={}, gateway={}",
                    currentUser.getEmail(), productId, gateway);

            BandwidthAddon addon = addonService.processPurchase(
                    currentUser, productId, purchaseToken, orderId, gateway, price, currency);

            result.put("success", true);
            result.put("message", "Bandwidth addon purchased successfully");
            result.put("addonId", addon.getId());
            result.put("bandwidthBytes", addon.getBandwidthBytes());
            result.put("bandwidthGB", addon.getBandwidthGB());

            log.info("Bandwidth addon purchase successful: user={}, product={}, GB={}",
                    currentUser.getEmail(), productId, addon.getBandwidthGB());

        } catch (Exception e) {
            log.error("Bandwidth addon purchase failed: {}", e.getMessage());
            result.put("success", false);
            result.put("message", "Purchase failed: " + e.getMessage());
            result.put("addonId", null);
            result.put("bandwidthBytes", null);
            result.put("bandwidthGB", null);
        }

        return result;
    }

    /**
     * Admin: Grant promotional bandwidth to a user.
     */
    @Secured(ADMIN)
    @MutationMapping
    public Map<String, Object> grantBandwidthToUser(@Argument Map<String, Object> input) {
        Map<String, Object> result = new HashMap<>();

        try {
            Integer userId = (Integer) input.get("userId");
            Double bandwidthGB = ((Number) input.get("bandwidthGB")).doubleValue();
            String notes = (String) input.get("notes");

            User user = userService.getUserById(userId);
            if (user == null) {
                throw new RuntimeException("User not found: " + userId);
            }

            long bandwidthBytes = (long) (bandwidthGB * BYTES_PER_GB);

            log.info("Admin granting {} GB bandwidth to user {}", bandwidthGB, user.getEmail());

            BandwidthAddon addon = addonService.grantPromotionalBandwidth(user, bandwidthBytes, notes);

            result.put("success", true);
            result.put("message", String.format("Granted %.2f GB bandwidth to user %s", bandwidthGB, user.getEmail()));
            result.put("addonId", addon.getId());
            result.put("bandwidthBytes", addon.getBandwidthBytes());
            result.put("bandwidthGB", addon.getBandwidthGB());

        } catch (Exception e) {
            log.error("Failed to grant bandwidth: {}", e.getMessage());
            result.put("success", false);
            result.put("message", "Failed: " + e.getMessage());
            result.put("addonId", null);
            result.put("bandwidthBytes", null);
            result.put("bandwidthGB", null);
        }

        return result;
    }
}
