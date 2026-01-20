package com.orbvpn.api.service.reseller;

import com.orbvpn.api.domain.dto.DevicePriceCalculation;
import com.orbvpn.api.domain.dto.ResellerDeviceAddResult;
import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.domain.enums.ResellerLevelName;
import com.orbvpn.api.exception.BadRequestException;
import com.orbvpn.api.exception.InsufficientFundsException;
import com.orbvpn.api.exception.NotFoundException;
import com.orbvpn.api.repository.ResellerAddCreditRepository;
import com.orbvpn.api.repository.ResellerRepository;
import com.orbvpn.api.service.RadiusService;
import com.orbvpn.api.service.UserService;
import com.orbvpn.api.service.AsyncNotificationHelper;
import com.orbvpn.api.service.device.DevicePricingService;
import com.orbvpn.api.service.subscription.UserSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for managing reseller device addon operations.
 * Handles device addition with proper credit charging.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ResellerDeviceService {

    private final DevicePricingService devicePricingService;
    private final UserSubscriptionService userSubscriptionService;
    private final ResellerRepository resellerRepository;
    private final ResellerAddCreditRepository resellerAddCreditRepository;
    private final RadiusService radiusService;
    private final UserService userService;
    private final AsyncNotificationHelper asyncNotificationHelper;

    /**
     * Calculate the price for adding devices to a user's subscription.
     * Does not perform the actual addition.
     *
     * @param resellerId  The reseller ID
     * @param userId      The user ID
     * @param deviceCount Number of devices to add
     * @return Price calculation details
     */
    public DevicePriceCalculation calculateDevicePrice(int resellerId, int userId, int deviceCount) {
        Reseller reseller = resellerRepository.findById(resellerId)
                .orElseThrow(() -> new NotFoundException("Reseller not found"));

        User user = userService.getUserById(userId);
        validateResellerUserAccess(reseller, user);

        return devicePricingService.calculateResellerDevicePrice(reseller, user, deviceCount);
    }

    /**
     * Add devices to a user's subscription with proper credit charging.
     *
     * @param resellerId  The reseller ID
     * @param userId      The user ID
     * @param deviceCount Number of devices to add
     * @return Result of the operation
     */
    @Transactional
    public ResellerDeviceAddResult addDevicesToUser(int resellerId, int userId, int deviceCount) {
        if (deviceCount <= 0) {
            throw new BadRequestException("Device count must be positive");
        }

        // Lock reseller to prevent race conditions on credit
        Reseller reseller = resellerRepository.findByIdWithLock(resellerId)
                .orElseThrow(() -> new NotFoundException("Reseller not found"));

        User user = userService.getUserById(userId);
        validateResellerUserAccess(reseller, user);

        UserSubscription subscription = userSubscriptionService.getCurrentSubscription(user);
        if (subscription == null) {
            throw new NotFoundException("User has no active subscription");
        }

        int currentDevices = subscription.getMultiLoginCount();
        int newDeviceCount = currentDevices + deviceCount;

        // Calculate price
        DevicePriceCalculation priceCalc = devicePricingService.calculateResellerDevicePrice(
                reseller, user, deviceCount);

        BigDecimal price = priceCalc.getFinalPrice();
        BigDecimal currentCredit = reseller.getCredit();

        // Check if reseller has sufficient credit (skip for OWNER level)
        if (reseller.getLevel().getName() != ResellerLevelName.OWNER) {
            if (currentCredit.compareTo(price) < 0) {
                log.warn("Reseller {} has insufficient credit. Required: {}, Available: {}",
                        resellerId, price, currentCredit);
                throw new InsufficientFundsException(
                        String.format("Insufficient credit. Required: $%.2f, Available: $%.2f",
                                price, currentCredit));
            }

            // Deduct credit
            BigDecimal newBalance = currentCredit.subtract(price);
            reseller.setCredit(newBalance);
            resellerRepository.save(reseller);

            // Record credit transaction
            ResellerAddCredit creditTransaction = ResellerAddCredit.createDevicePurchase(
                    reseller, price, newBalance, userId, user.getEmail(), deviceCount);
            resellerAddCreditRepository.save(creditTransaction);

            log.info("Deducted {} credit from reseller {} for {} devices. New balance: {}",
                    price, resellerId, deviceCount, newBalance);
        }

        // Update user's device count
        subscription.setMultiLoginCount(newDeviceCount);
        userSubscriptionService.saveUserSubscription(subscription);
        radiusService.editUserMoreLoginCount(user, newDeviceCount);

        log.info("Successfully added {} devices for user {} by reseller {}. Total devices: {}",
                deviceCount, userId, resellerId, newDeviceCount);

        // Trigger webhook event
        triggerDeviceAddedWebhook(reseller, user, deviceCount, price, newDeviceCount);

        return ResellerDeviceAddResult.builder()
                .success(true)
                .userId(userId)
                .userEmail(user.getEmail())
                .devicesAdded(deviceCount)
                .totalDevices(newDeviceCount)
                .amountCharged(price)
                .resellerNewBalance(reseller.getCredit())
                .message(String.format("Successfully added %d devices. Charged: $%.2f",
                        deviceCount, price))
                .build();
    }

    /**
     * Set exact device count for a user (can increase or decrease).
     * Only charges for increases.
     *
     * @param resellerId      The reseller ID
     * @param userId          The user ID
     * @param newDeviceCount  New total device count
     * @return Result of the operation
     */
    @Transactional
    public ResellerDeviceAddResult setUserDeviceCount(int resellerId, int userId, int newDeviceCount) {
        if (newDeviceCount < 1) {
            throw new BadRequestException("Device count must be at least 1");
        }

        Reseller reseller = resellerRepository.findByIdWithLock(resellerId)
                .orElseThrow(() -> new NotFoundException("Reseller not found"));

        User user = userService.getUserById(userId);
        validateResellerUserAccess(reseller, user);

        UserSubscription subscription = userSubscriptionService.getCurrentSubscription(user);
        if (subscription == null) {
            throw new NotFoundException("User has no active subscription");
        }

        int currentDevices = subscription.getMultiLoginCount();
        int deviceDifference = newDeviceCount - currentDevices;

        if (deviceDifference == 0) {
            return ResellerDeviceAddResult.builder()
                    .success(true)
                    .userId(userId)
                    .userEmail(user.getEmail())
                    .devicesAdded(0)
                    .totalDevices(currentDevices)
                    .amountCharged(BigDecimal.ZERO)
                    .resellerNewBalance(reseller.getCredit())
                    .message("Device count unchanged")
                    .build();
        }

        if (deviceDifference > 0) {
            // Adding devices - charge for the difference
            return addDevicesToUser(resellerId, userId, deviceDifference);
        } else {
            // Reducing devices - no charge (no refund in standard model)
            subscription.setMultiLoginCount(newDeviceCount);
            userSubscriptionService.saveUserSubscription(subscription);
            radiusService.editUserMoreLoginCount(user, newDeviceCount);

            log.info("Reseller {} reduced devices for user {} from {} to {}",
                    resellerId, userId, currentDevices, newDeviceCount);

            return ResellerDeviceAddResult.builder()
                    .success(true)
                    .userId(userId)
                    .userEmail(user.getEmail())
                    .devicesAdded(deviceDifference) // Negative value
                    .totalDevices(newDeviceCount)
                    .amountCharged(BigDecimal.ZERO)
                    .resellerNewBalance(reseller.getCredit())
                    .message(String.format("Reduced devices from %d to %d. No refund.",
                            currentDevices, newDeviceCount))
                    .build();
        }
    }

    /**
     * Get current device count for a user.
     */
    public int getUserDeviceCount(int userId) {
        User user = userService.getUserById(userId);
        UserSubscription subscription = userSubscriptionService.getCurrentSubscription(user);
        return subscription != null ? subscription.getMultiLoginCount() : 0;
    }

    /**
     * Validate that reseller has access to the user.
     */
    private void validateResellerUserAccess(Reseller reseller, User user) {
        if (user.getReseller() == null || user.getReseller().getId() != reseller.getId()) {
            // Check if accessor is admin - admins can manage any user
            if (!userService.isAdmin()) {
                throw new BadRequestException("User does not belong to this reseller");
            }
        }
    }

    /**
     * Trigger webhook for device addition.
     */
    private void triggerDeviceAddedWebhook(Reseller reseller, User user, int devicesAdded,
                                            BigDecimal amountCharged, int totalDevices) {
        Map<String, Object> extraData = new HashMap<>();
        extraData.put("resellerId", reseller.getId());
        extraData.put("resellerEmail", reseller.getUser().getEmail());
        extraData.put("devicesAdded", devicesAdded);
        extraData.put("totalDevices", totalDevices);
        extraData.put("amountCharged", amountCharged);
        extraData.put("timestamp", LocalDateTime.now().toString());

        asyncNotificationHelper.sendUserWebhookWithExtraAsync(user, "RESELLER_DEVICES_ADDED", extraData);
    }
}
