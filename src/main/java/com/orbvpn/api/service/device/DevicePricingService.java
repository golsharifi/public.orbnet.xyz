package com.orbvpn.api.service.device;

import com.orbvpn.api.domain.dto.DevicePriceCalculation;
import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.domain.enums.ResellerLevelName;
import com.orbvpn.api.exception.BadRequestException;
import com.orbvpn.api.exception.NotFoundException;
import com.orbvpn.api.service.UserService;
import com.orbvpn.api.service.subscription.UserSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Centralized service for calculating device/multi-login pricing.
 * Handles pro-rata calculations for users and resellers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DevicePricingService {

    private final UserSubscriptionService userSubscriptionService;
    private final UserService userService;

    // Minimum charge to prevent $0 charges for very short periods
    private static final BigDecimal MINIMUM_CHARGE = new BigDecimal("0.50");

    /**
     * Calculate pro-rata price for additional devices for a user.
     * Price is based on remaining subscription time.
     *
     * @param deviceCount Number of additional devices
     * @return Calculated price details
     */
    public DevicePriceCalculation calculateUserDevicePrice(int deviceCount) {
        User user = userService.getUser();
        return calculateUserDevicePrice(user, deviceCount);
    }

    /**
     * Calculate pro-rata price for additional devices for a specific user.
     *
     * @param user        The user
     * @param deviceCount Number of additional devices
     * @return Calculated price details
     */
    public DevicePriceCalculation calculateUserDevicePrice(User user, int deviceCount) {
        if (deviceCount <= 0) {
            throw new BadRequestException("Device count must be positive");
        }

        UserSubscription subscription = userSubscriptionService.getCurrentSubscription(user);
        if (subscription == null) {
            throw new NotFoundException("No active subscription found for user");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = subscription.getExpiresAt();

        if (expiresAt == null || expiresAt.isBefore(now)) {
            throw new BadRequestException("Subscription has expired. Please renew first.");
        }

        long remainingDays = ChronoUnit.DAYS.between(now, expiresAt);
        if (remainingDays <= 0) {
            remainingDays = 1; // Minimum 1 day
        }

        int subscriptionDuration = subscription.getDuration();
        if (subscriptionDuration <= 0) {
            subscriptionDuration = 30; // Default to 30 days if not set
        }

        Group group = subscription.getGroup();
        BigDecimal groupPrice = group.getPrice();
        int baseDeviceCount = group.getMultiLoginCount();

        // Calculate price per device per day
        // Formula: (groupPrice / subscriptionDuration) / baseDeviceCount
        BigDecimal dailySubscriptionRate = groupPrice.divide(
                BigDecimal.valueOf(subscriptionDuration), 6, RoundingMode.HALF_UP);

        BigDecimal dailyRatePerDevice = dailySubscriptionRate.divide(
                BigDecimal.valueOf(Math.max(baseDeviceCount, 1)), 6, RoundingMode.HALF_UP);

        // Apply service group discount if available
        BigDecimal discountMultiplier = BigDecimal.ONE;
        ServiceGroup serviceGroup = group.getServiceGroup();
        if (serviceGroup != null && serviceGroup.getDiscount() != null) {
            BigDecimal discountPercent = serviceGroup.getDiscount();
            discountMultiplier = BigDecimal.ONE.subtract(
                    discountPercent.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
        }

        // Calculate final price
        BigDecimal priceBeforeDiscount = dailyRatePerDevice
                .multiply(BigDecimal.valueOf(deviceCount))
                .multiply(BigDecimal.valueOf(remainingDays));

        BigDecimal finalPrice = priceBeforeDiscount
                .multiply(discountMultiplier)
                .setScale(2, RoundingMode.HALF_UP);

        // Apply minimum charge
        if (finalPrice.compareTo(MINIMUM_CHARGE) < 0) {
            finalPrice = MINIMUM_CHARGE;
        }

        log.info("Calculated device price for user {}: {} devices, {} remaining days, price: {}",
                user.getId(), deviceCount, remainingDays, finalPrice);

        return DevicePriceCalculation.builder()
                .deviceCount(deviceCount)
                .remainingDays((int) remainingDays)
                .subscriptionDuration(subscriptionDuration)
                .dailyRatePerDevice(dailyRatePerDevice.setScale(4, RoundingMode.HALF_UP))
                .priceBeforeDiscount(priceBeforeDiscount.setScale(2, RoundingMode.HALF_UP))
                .discountPercent(serviceGroup != null ? serviceGroup.getDiscount() : BigDecimal.ZERO)
                .finalPrice(finalPrice)
                .currency("USD")
                .subscriptionExpiresAt(expiresAt)
                .build();
    }

    /**
     * Calculate price for reseller to add devices to a user's subscription.
     * Includes reseller level discount.
     *
     * @param reseller    The reseller
     * @param user        The user receiving devices
     * @param deviceCount Number of additional devices
     * @return Calculated price details
     */
    public DevicePriceCalculation calculateResellerDevicePrice(Reseller reseller, User user, int deviceCount) {
        if (deviceCount <= 0) {
            throw new BadRequestException("Device count must be positive");
        }

        // Owner level gets free devices
        if (reseller.getLevel().getName() == ResellerLevelName.OWNER) {
            log.info("Reseller {} is OWNER level - devices are free", reseller.getId());
            return DevicePriceCalculation.builder()
                    .deviceCount(deviceCount)
                    .remainingDays(0)
                    .subscriptionDuration(0)
                    .dailyRatePerDevice(BigDecimal.ZERO)
                    .priceBeforeDiscount(BigDecimal.ZERO)
                    .discountPercent(new BigDecimal("100"))
                    .finalPrice(BigDecimal.ZERO)
                    .currency("USD")
                    .resellerDiscount(new BigDecimal("100"))
                    .build();
        }

        UserSubscription subscription = userSubscriptionService.getCurrentSubscription(user);
        if (subscription == null) {
            throw new NotFoundException("No active subscription found for user");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = subscription.getExpiresAt();

        if (expiresAt == null || expiresAt.isBefore(now)) {
            throw new BadRequestException("User subscription has expired");
        }

        long remainingDays = ChronoUnit.DAYS.between(now, expiresAt);
        if (remainingDays <= 0) {
            remainingDays = 1;
        }

        int subscriptionDuration = subscription.getDuration();
        if (subscriptionDuration <= 0) {
            subscriptionDuration = 30;
        }

        Group group = subscription.getGroup();
        BigDecimal groupPrice = group.getPrice();
        int baseDeviceCount = group.getMultiLoginCount();

        // Calculate base price per device per day
        BigDecimal dailySubscriptionRate = groupPrice.divide(
                BigDecimal.valueOf(subscriptionDuration), 6, RoundingMode.HALF_UP);

        BigDecimal dailyRatePerDevice = dailySubscriptionRate.divide(
                BigDecimal.valueOf(Math.max(baseDeviceCount, 1)), 6, RoundingMode.HALF_UP);

        // Calculate price before reseller discount
        BigDecimal priceBeforeDiscount = dailyRatePerDevice
                .multiply(BigDecimal.valueOf(deviceCount))
                .multiply(BigDecimal.valueOf(remainingDays));

        // Apply reseller level discount
        ResellerLevel level = reseller.getLevel();
        BigDecimal resellerDiscountPercent = level.getDiscountPercent();
        BigDecimal resellerDiscountMultiplier = BigDecimal.ONE.subtract(
                resellerDiscountPercent.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));

        BigDecimal finalPrice = priceBeforeDiscount
                .multiply(resellerDiscountMultiplier)
                .setScale(2, RoundingMode.HALF_UP);

        // Apply minimum charge (even for resellers)
        if (finalPrice.compareTo(MINIMUM_CHARGE) < 0 && finalPrice.compareTo(BigDecimal.ZERO) > 0) {
            finalPrice = MINIMUM_CHARGE;
        }

        log.info("Calculated reseller device price: reseller={}, user={}, devices={}, remainingDays={}, " +
                        "resellerDiscount={}%, price={}",
                reseller.getId(), user.getId(), deviceCount, remainingDays,
                resellerDiscountPercent, finalPrice);

        return DevicePriceCalculation.builder()
                .deviceCount(deviceCount)
                .remainingDays((int) remainingDays)
                .subscriptionDuration(subscriptionDuration)
                .dailyRatePerDevice(dailyRatePerDevice.setScale(4, RoundingMode.HALF_UP))
                .priceBeforeDiscount(priceBeforeDiscount.setScale(2, RoundingMode.HALF_UP))
                .discountPercent(BigDecimal.ZERO) // Service group discount not applied for resellers
                .resellerDiscount(resellerDiscountPercent)
                .finalPrice(finalPrice)
                .currency("USD")
                .subscriptionExpiresAt(expiresAt)
                .build();
    }

    /**
     * Calculate flat-rate price for device addon (for IAP).
     * Uses ExtraLoginsPlan pricing.
     *
     * @param plan     The extra logins plan
     * @param quantity Number of plans to purchase
     * @return Calculated price
     */
    public DevicePriceCalculation calculatePlanBasedPrice(ExtraLoginsPlan plan, int quantity) {
        if (quantity <= 0) {
            throw new BadRequestException("Quantity must be positive");
        }

        BigDecimal basePrice = plan.getBasePrice();
        BigDecimal totalPrice = basePrice.multiply(BigDecimal.valueOf(quantity));

        // Apply bulk discount if applicable
        BigDecimal bulkDiscount = BigDecimal.ZERO;
        if (quantity >= plan.getMinimumQuantity() && plan.getBulkDiscountPercent() != null) {
            bulkDiscount = plan.getBulkDiscountPercent();
            BigDecimal discountAmount = totalPrice.multiply(
                    bulkDiscount.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
            totalPrice = totalPrice.subtract(discountAmount);
        }

        return DevicePriceCalculation.builder()
                .deviceCount(plan.getLoginCount() * quantity)
                .dailyRatePerDevice(BigDecimal.ZERO) // Not applicable for plan-based
                .priceBeforeDiscount(basePrice.multiply(BigDecimal.valueOf(quantity)))
                .discountPercent(bulkDiscount)
                .finalPrice(totalPrice.setScale(2, RoundingMode.HALF_UP))
                .currency("USD")
                .planId(plan.getId())
                .planName(plan.getName())
                .durationDays(plan.getDurationDays())
                .build();
    }

    /**
     * Get the price difference when upgrading device count.
     *
     * @param user           The user
     * @param currentDevices Current device count
     * @param newDevices     New device count
     * @return Price to charge (or refund if negative)
     */
    public BigDecimal calculateDeviceUpgradePrice(User user, int currentDevices, int newDevices) {
        int deviceDifference = newDevices - currentDevices;

        if (deviceDifference == 0) {
            return BigDecimal.ZERO;
        }

        if (deviceDifference > 0) {
            // Adding devices - charge pro-rata
            DevicePriceCalculation calc = calculateUserDevicePrice(user, deviceDifference);
            return calc.getFinalPrice();
        } else {
            // Removing devices - no refund in most VPN business models
            log.info("User {} reducing devices from {} to {} - no refund",
                    user.getId(), currentDevices, newDevices);
            return BigDecimal.ZERO;
        }
    }
}
