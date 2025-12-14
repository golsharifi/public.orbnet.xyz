package com.orbvpn.api.service.device;

import com.orbvpn.api.domain.dto.DeviceAddonPurchaseResult;
import com.orbvpn.api.domain.entity.Payment;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.domain.enums.GatewayName;
import com.orbvpn.api.domain.enums.PaymentCategory;
import com.orbvpn.api.domain.enums.PaymentStatus;
import com.orbvpn.api.exception.PaymentException;
import com.orbvpn.api.repository.PaymentRepository;
import com.orbvpn.api.service.RadiusService;
import com.orbvpn.api.service.UserService;
import com.orbvpn.api.service.AsyncNotificationHelper;
import com.orbvpn.api.service.subscription.UserSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for handling In-App Purchase (IAP) validation and fulfillment
 * for device addon purchases from Apple App Store and Google Play.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DeviceAddonIAPService {

    private final PaymentRepository paymentRepository;
    private final UserSubscriptionService userSubscriptionService;
    private final RadiusService radiusService;
    private final UserService userService;
    private final AsyncNotificationHelper asyncNotificationHelper;

    // Product ID pattern: device_addon_X where X is the device count
    private static final Pattern DEVICE_ADDON_PATTERN = Pattern.compile("device_addon_(\\d+)");

    // Consumable product IDs for devices (can be configured)
    private static final Map<String, Integer> DEVICE_PRODUCT_MAP = new HashMap<>() {{
        put("com.orbvpn.device_addon_1", 1);
        put("com.orbvpn.device_addon_2", 2);
        put("com.orbvpn.device_addon_3", 3);
        put("com.orbvpn.device_addon_5", 5);
        put("com.orbvpn.device_addon_10", 10);
        // Alternative naming
        put("device_addon_1", 1);
        put("device_addon_2", 2);
        put("device_addon_3", 3);
        put("device_addon_5", 5);
        put("device_addon_10", 10);
    }};

    /**
     * Process Apple In-App Purchase for device addon.
     *
     * @param receipt              The Apple receipt data
     * @param productId            The product ID purchased
     * @param transactionId        The transaction ID from Apple
     * @param originalTransactionId Original transaction ID for tracking
     * @return Purchase result
     */
    public DeviceAddonPurchaseResult processApplePurchase(
            String receipt,
            String productId,
            String transactionId,
            String originalTransactionId) {

        log.info("Processing Apple device addon purchase: product={}, txn={}", productId, transactionId);

        try {
            User user = userService.getUser();

            // Validate product and get device count
            int deviceCount = parseDeviceCount(productId);
            if (deviceCount <= 0) {
                return DeviceAddonPurchaseResult.builder()
                        .success(false)
                        .message("Invalid product ID: " + productId)
                        .errorCode("INVALID_PRODUCT")
                        .build();
            }

            // Check for duplicate transaction
            if (paymentRepository.existsByPaymentIdAndGateway(transactionId, GatewayName.APPLE_STORE)) {
                log.warn("Duplicate Apple transaction: {}", transactionId);
                return DeviceAddonPurchaseResult.builder()
                        .success(false)
                        .message("Transaction already processed")
                        .errorCode("DUPLICATE_TRANSACTION")
                        .build();
            }

            // Create and save payment record
            Payment payment = createPayment(user, deviceCount, GatewayName.APPLE_STORE, transactionId);

            // Fulfill the purchase
            return fulfillDeviceAddon(user, payment, deviceCount);

        } catch (Exception e) {
            log.error("Error processing Apple device addon purchase: {}", e.getMessage(), e);
            return DeviceAddonPurchaseResult.builder()
                    .success(false)
                    .message("Failed to process purchase: " + e.getMessage())
                    .errorCode("PROCESSING_ERROR")
                    .build();
        }
    }

    /**
     * Process Google Play In-App Purchase for device addon.
     *
     * @param purchaseToken The purchase token from Google Play
     * @param productId     The product ID purchased
     * @param orderId       The order ID from Google Play
     * @return Purchase result
     */
    public DeviceAddonPurchaseResult processGooglePlayPurchase(
            String purchaseToken,
            String productId,
            String orderId) {

        log.info("Processing Google Play device addon purchase: product={}, order={}", productId, orderId);

        try {
            User user = userService.getUser();

            // Validate product and get device count
            int deviceCount = parseDeviceCount(productId);
            if (deviceCount <= 0) {
                return DeviceAddonPurchaseResult.builder()
                        .success(false)
                        .message("Invalid product ID: " + productId)
                        .errorCode("INVALID_PRODUCT")
                        .build();
            }

            // Check for duplicate transaction
            String transactionId = orderId != null ? orderId : purchaseToken;
            if (paymentRepository.existsByPaymentIdAndGateway(transactionId, GatewayName.GOOGLE_PLAY)) {
                log.warn("Duplicate Google Play transaction: {}", transactionId);
                return DeviceAddonPurchaseResult.builder()
                        .success(false)
                        .message("Transaction already processed")
                        .errorCode("DUPLICATE_TRANSACTION")
                        .build();
            }

            // Create and save payment record
            Payment payment = createPayment(user, deviceCount, GatewayName.GOOGLE_PLAY, transactionId);

            // Fulfill the purchase
            return fulfillDeviceAddon(user, payment, deviceCount);

        } catch (Exception e) {
            log.error("Error processing Google Play device addon purchase: {}", e.getMessage(), e);
            return DeviceAddonPurchaseResult.builder()
                    .success(false)
                    .message("Failed to process purchase: " + e.getMessage())
                    .errorCode("PROCESSING_ERROR")
                    .build();
        }
    }

    /**
     * Parse device count from product ID.
     */
    private int parseDeviceCount(String productId) {
        // First check the map
        Integer count = DEVICE_PRODUCT_MAP.get(productId);
        if (count != null) {
            return count;
        }

        // Try to parse from pattern
        Matcher matcher = DEVICE_ADDON_PATTERN.matcher(productId);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                log.warn("Failed to parse device count from product ID: {}", productId);
            }
        }

        // Try extracting from full product ID
        if (productId.contains("device_addon_")) {
            String[] parts = productId.split("device_addon_");
            if (parts.length > 1) {
                try {
                    return Integer.parseInt(parts[1].replaceAll("[^0-9]", ""));
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse device count from product ID: {}", productId);
                }
            }
        }

        return 0;
    }

    /**
     * Create payment record for device addon.
     */
    private Payment createPayment(User user, int deviceCount, GatewayName gateway, String transactionId) {
        Payment payment = Payment.builder()
                .user(user)
                .status(PaymentStatus.PENDING)
                .gateway(gateway)
                .category(PaymentCategory.MORE_LOGIN)
                .price(BigDecimal.ZERO) // IAP price is handled by store
                .moreLoginCount(deviceCount)
                .paymentId(transactionId)
                .build();

        return paymentRepository.save(payment);
    }

    /**
     * Fulfill device addon purchase - add devices to user's subscription.
     */
    private DeviceAddonPurchaseResult fulfillDeviceAddon(User user, Payment payment, int deviceCount) {
        try {
            UserSubscription subscription = userSubscriptionService.getCurrentSubscription(user);
            if (subscription == null) {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setErrorMessage("No active subscription");
                paymentRepository.save(payment);

                return DeviceAddonPurchaseResult.builder()
                        .success(false)
                        .userId(user.getId())
                        .message("No active subscription found")
                        .errorCode("NO_SUBSCRIPTION")
                        .build();
            }

            int currentDevices = subscription.getMultiLoginCount();
            int newDeviceCount = currentDevices + deviceCount;

            // Update subscription
            subscription.setMultiLoginCount(newDeviceCount);
            userSubscriptionService.saveUserSubscription(subscription);

            // Update radius
            radiusService.editUserMoreLoginCount(user, newDeviceCount);

            // Mark payment successful
            payment.setStatus(PaymentStatus.SUCCEEDED);
            payment.setExpiresAt(subscription.getExpiresAt());
            paymentRepository.save(payment);

            log.info("Successfully added {} devices for user {}. New total: {}",
                    deviceCount, user.getId(), newDeviceCount);

            // Send notification using generic system notification
            try {
                Map<String, Object> notificationData = new HashMap<>();
                notificationData.put("devicesAdded", deviceCount);
                notificationData.put("totalDevices", newDeviceCount);
                notificationData.put("type", "device_addon_purchased");
                // Notification can be handled by the webhook receiver or FCM
                log.info("Device addon purchase notification data: {}", notificationData);
            } catch (Exception e) {
                log.warn("Failed to log device addon notification: {}", e.getMessage());
            }

            // Trigger webhook
            triggerWebhook(user, deviceCount, newDeviceCount, payment.getGateway().name());

            return DeviceAddonPurchaseResult.builder()
                    .success(true)
                    .userId(user.getId())
                    .devicesAdded(deviceCount)
                    .totalDevices(newDeviceCount)
                    .paymentId(payment.getPaymentId())
                    .message(String.format("Successfully added %d devices", deviceCount))
                    .build();

        } catch (Exception e) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setErrorMessage(e.getMessage());
            paymentRepository.save(payment);

            throw new PaymentException("Failed to fulfill device addon: " + e.getMessage());
        }
    }

    /**
     * Trigger webhook for device addon purchase.
     */
    private void triggerWebhook(User user, int devicesAdded, int totalDevices, String gateway) {
        Map<String, Object> extraData = new HashMap<>();
        extraData.put("devicesAdded", devicesAdded);
        extraData.put("totalDevices", totalDevices);
        extraData.put("gateway", gateway);
        extraData.put("purchaseType", "IAP_DEVICE_ADDON");
        extraData.put("timestamp", LocalDateTime.now().toString());

        asyncNotificationHelper.sendUserWebhookWithExtraAsync(user, "DEVICE_ADDON_PURCHASED", extraData);
    }

    /**
     * Get available device addon products for mobile apps.
     */
    public Map<String, Integer> getAvailableProducts() {
        return new HashMap<>(DEVICE_PRODUCT_MAP);
    }

    /**
     * Validate that a product ID is a valid device addon product.
     */
    public boolean isValidDeviceAddonProduct(String productId) {
        return parseDeviceCount(productId) > 0;
    }
}
