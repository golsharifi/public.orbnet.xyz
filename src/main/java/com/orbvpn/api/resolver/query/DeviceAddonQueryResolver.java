package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.dto.DevicePriceCalculation;
import com.orbvpn.api.domain.dto.DeviceUsageStats;
import com.orbvpn.api.domain.dto.ResellerDeviceRevenue;
import com.orbvpn.api.domain.entity.Reseller;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.service.UserService;
import com.orbvpn.api.service.device.DeviceAnalyticsService;
import com.orbvpn.api.service.device.DevicePricingService;
import com.orbvpn.api.service.reseller.ResellerDeviceRevenueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;

import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;
import static com.orbvpn.api.domain.enums.RoleName.Constants.RESELLER;
import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;

/**
 * GraphQL query resolver for device addon pricing, analytics, and reporting.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class DeviceAddonQueryResolver {

    private final DevicePricingService devicePricingService;
    private final DeviceAnalyticsService deviceAnalyticsService;
    private final ResellerDeviceRevenueService revenueService;
    private final UserService userService;

    /**
     * Calculate pro-rata price for adding devices to the current user's subscription.
     *
     * @param deviceCount Number of devices to add
     * @return Detailed price calculation
     */
    @Secured(USER)
    @QueryMapping
    public DevicePriceCalculation calculateDeviceAddonPrice(
            @Argument @Valid @Min(value = 1, message = "Device count must be at least 1") Integer deviceCount) {
        log.info("Calculating device addon price for {} devices", deviceCount);
        try {
            DevicePriceCalculation calculation = devicePricingService.calculateUserDevicePrice(deviceCount);
            log.info("Device addon price calculated: {} for {} devices",
                    calculation.getFinalPrice(), deviceCount);
            return calculation;
        } catch (Exception e) {
            log.error("Error calculating device addon price: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Calculate pro-rata price for reseller to add devices to a user.
     * Includes reseller level discount.
     *
     * @param userId      The user ID
     * @param deviceCount Number of devices to add
     * @return Detailed price calculation with reseller discount
     */
    @Secured({ADMIN, RESELLER})
    @QueryMapping
    public DevicePriceCalculation calculateResellerDevicePrice(
            @Argument @Valid @Min(value = 1, message = "User ID must be positive") Integer userId,
            @Argument @Valid @Min(value = 1, message = "Device count must be at least 1") Integer deviceCount) {
        log.info("Calculating reseller device price for user {} - {} devices", userId, deviceCount);
        try {
            User accessor = userService.getUser();
            Reseller reseller = accessor.getReseller();

            if (reseller == null && !userService.isAdmin()) {
                throw new IllegalStateException("Only resellers or admins can calculate reseller device prices");
            }

            User user = userService.getUserById(userId);

            // For admins without reseller, use the user's reseller
            if (reseller == null) {
                reseller = user.getReseller();
            }

            if (reseller == null) {
                throw new IllegalStateException("No reseller found for pricing calculation");
            }

            DevicePriceCalculation calculation = devicePricingService.calculateResellerDevicePrice(
                    reseller, user, deviceCount);

            log.info("Reseller device price calculated: {} for user {} - {} devices",
                    calculation.getFinalPrice(), userId, deviceCount);
            return calculation;
        } catch (Exception e) {
            log.error("Error calculating reseller device price for user {}: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Get device usage statistics for the current user.
     */
    @Secured(USER)
    @QueryMapping
    public DeviceUsageStats getDeviceUsageStats() {
        log.info("Getting device usage stats for current user");
        return deviceAnalyticsService.getUserDeviceStats();
    }

    /**
     * Get device usage statistics for a specific user.
     * Admin/Reseller only.
     */
    @Secured({ADMIN, RESELLER})
    @QueryMapping
    public DeviceUsageStats getUserDeviceStats(
            @Argument @Valid @Min(value = 1) Integer userId) {
        log.info("Getting device usage stats for user {}", userId);
        User user = userService.getUserById(userId);
        return deviceAnalyticsService.getUserDeviceStats(user);
    }

    /**
     * Get reseller device revenue report.
     * Admin/Reseller only.
     */
    @Secured({ADMIN, RESELLER})
    @QueryMapping
    public ResellerDeviceRevenue getResellerDeviceRevenue(
            @Argument @Nullable Integer resellerId,
            @Argument @Nullable LocalDateTime startDate,
            @Argument @Nullable LocalDateTime endDate) {

        // If no resellerId provided, use current user's reseller
        if (resellerId == null) {
            User accessor = userService.getUser();
            Reseller reseller = accessor.getReseller();
            if (reseller != null) {
                resellerId = reseller.getId();
            } else if (!userService.isAdmin()) {
                throw new IllegalStateException("Reseller ID required for non-admin users");
            } else {
                throw new IllegalArgumentException("Reseller ID required for admin access");
            }
        }

        log.info("Getting device revenue for reseller {}", resellerId);
        return revenueService.getDeviceRevenue(resellerId, startDate, endDate);
    }
}
