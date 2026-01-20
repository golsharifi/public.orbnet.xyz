package com.orbvpn.api.service.staticip;

import com.orbvpn.api.domain.dto.staticip.StaticIPSubscriptionResponse;
import com.orbvpn.api.domain.entity.Payment;
import com.orbvpn.api.domain.entity.StaticIPSubscription;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.enums.*;
import com.orbvpn.api.repository.PaymentRepository;
import com.orbvpn.api.service.UserService;
import com.orbvpn.api.service.iap.AppleReceiptValidationService;
import com.orbvpn.api.service.iap.AppleReceiptValidationService.AppleReceiptValidationResult;
import com.orbvpn.api.service.iap.GooglePlayValidationService;
import com.orbvpn.api.service.iap.GooglePlayValidationService.GooglePlayValidationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for handling In-App Purchase (IAP) validation and fulfillment
 * for Static IP subscriptions from Apple App Store and Google Play.
 *
 * Flow:
 * 1. Flutter calls createStaticIPSubscription with paymentMethod=APPLE_STORE
 * 2. Backend returns mobileProductId (e.g., "ios_staticip_personal")
 * 3. Flutter completes purchase via StoreKit/Google Play Billing
 * 4. Flutter calls verifyStaticIPApplePurchase/verifyStaticIPGooglePlayPurchase
 * 5. Backend verifies receipt and creates subscription
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class StaticIPIAPService {

    private final StaticIPService staticIPService;
    private final PaymentRepository paymentRepository;
    private final UserService userService;
    private final AppleReceiptValidationService appleReceiptValidationService;
    private final GooglePlayValidationService googlePlayValidationService;

    @Value("${iap.validation.enabled:true}")
    private boolean validationEnabled;

    // Product ID patterns for Static IP plans
    // iOS: ios_staticip_personal, ios_staticip_pro, etc.
    // Android: android_staticip_personal, android_staticip_pro, etc.
    private static final Pattern IOS_PRODUCT_PATTERN = Pattern.compile("ios_staticip_(\\w+)");
    private static final Pattern ANDROID_PRODUCT_PATTERN = Pattern.compile("android_staticip_(\\w+)");

    // Product ID to plan type mapping
    private static final Map<String, StaticIPPlanType> PRODUCT_TO_PLAN = new HashMap<>() {{
        // iOS products
        put("ios_staticip_personal", StaticIPPlanType.PERSONAL);
        put("ios_staticip_pro", StaticIPPlanType.PRO);
        put("ios_staticip_multi_region", StaticIPPlanType.MULTI_REGION);
        put("ios_staticip_business", StaticIPPlanType.BUSINESS);
        put("ios_staticip_enterprise", StaticIPPlanType.ENTERPRISE);
        // Alternative iOS naming (App Store Connect often uses dots)
        put("com.orbvpn.staticip.personal", StaticIPPlanType.PERSONAL);
        put("com.orbvpn.staticip.pro", StaticIPPlanType.PRO);
        put("com.orbvpn.staticip.multiregion", StaticIPPlanType.MULTI_REGION);
        put("com.orbvpn.staticip.business", StaticIPPlanType.BUSINESS);
        put("com.orbvpn.staticip.enterprise", StaticIPPlanType.ENTERPRISE);
        // Android products
        put("android_staticip_personal", StaticIPPlanType.PERSONAL);
        put("android_staticip_pro", StaticIPPlanType.PRO);
        put("android_staticip_multi_region", StaticIPPlanType.MULTI_REGION);
        put("android_staticip_business", StaticIPPlanType.BUSINESS);
        put("android_staticip_enterprise", StaticIPPlanType.ENTERPRISE);
        // Alternative Android naming
        put("staticip_personal", StaticIPPlanType.PERSONAL);
        put("staticip_pro", StaticIPPlanType.PRO);
        put("staticip_multi_region", StaticIPPlanType.MULTI_REGION);
        put("staticip_business", StaticIPPlanType.BUSINESS);
        put("staticip_enterprise", StaticIPPlanType.ENTERPRISE);
    }};

    /**
     * Process Apple In-App Purchase for Static IP subscription.
     *
     * @param receipt              The Apple receipt data (base64 encoded)
     * @param productId            The product ID purchased
     * @param transactionId        The transaction ID from Apple
     * @param originalTransactionId Original transaction ID for subscription tracking
     * @return Subscription response
     */
    public StaticIPSubscriptionResponse processApplePurchase(
            String receipt,
            String productId,
            String transactionId,
            String originalTransactionId) {

        log.info("Processing Apple Static IP purchase: product={}, txn={}", productId, transactionId);

        try {
            User user = userService.getUser();

            // Check for existing subscription
            if (staticIPService.getUserSubscription(user).isPresent()) {
                return StaticIPSubscriptionResponse.builder()
                        .success(false)
                        .message("User already has an active static IP subscription")
                        .build();
            }

            // Parse plan type from product ID
            StaticIPPlanType planType = parsePlanType(productId);
            if (planType == null) {
                log.error("Invalid Static IP product ID: {}", productId);
                return StaticIPSubscriptionResponse.builder()
                        .success(false)
                        .message("Invalid product ID: " + productId)
                        .build();
            }

            // Check for duplicate transaction
            if (paymentRepository.existsByPaymentIdAndGateway(transactionId, GatewayName.APPLE_STORE)) {
                log.warn("Duplicate Apple transaction for Static IP: {}", transactionId);
                return StaticIPSubscriptionResponse.builder()
                        .success(false)
                        .message("Transaction already processed")
                        .build();
            }

            // Validate receipt with Apple's verifyReceipt API
            if (validationEnabled) {
                AppleReceiptValidationResult validationResult = appleReceiptValidationService.validateReceipt(
                        receipt, productId, transactionId);

                if (!validationResult.isValid()) {
                    log.error("Apple receipt validation failed: {}", validationResult.getErrorMessage());
                    return StaticIPSubscriptionResponse.builder()
                            .success(false)
                            .message("Receipt validation failed: " + validationResult.getErrorMessage())
                            .build();
                }

                // Check if subscription is expired
                if (validationResult.isExpired()) {
                    log.warn("Apple subscription is expired for transaction: {}", transactionId);
                    return StaticIPSubscriptionResponse.builder()
                            .success(false)
                            .message("Subscription has expired")
                            .build();
                }

                log.info("Apple receipt validated successfully. Product: {}, Expires: {}",
                        validationResult.getProductId(), validationResult.getExpiresDate());
            } else {
                log.warn("IAP validation is disabled. Skipping Apple receipt validation for transaction: {}", transactionId);
            }

            // Create payment record
            Payment payment = createPayment(user, planType, GatewayName.APPLE_STORE, transactionId, originalTransactionId);

            // Create subscription
            StaticIPSubscription subscription = staticIPService.createSubscription(
                    user,
                    planType,
                    true, // Auto-renew for IAP subscriptions
                    originalTransactionId != null ? originalTransactionId : transactionId
            );

            // Update payment status
            payment.setStatus(PaymentStatus.SUCCEEDED);
            paymentRepository.save(payment);

            log.info("Successfully created Static IP subscription via Apple IAP for user {}: plan={}",
                    user.getId(), planType);

            return StaticIPSubscriptionResponse.builder()
                    .success(true)
                    .message("Static IP subscription activated successfully")
                    .subscription(subscription)
                    .build();

        } catch (Exception e) {
            log.error("Error processing Apple Static IP purchase: {}", e.getMessage(), e);
            return StaticIPSubscriptionResponse.builder()
                    .success(false)
                    .message("Failed to process purchase: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Process Google Play In-App Purchase for Static IP subscription.
     *
     * @param purchaseToken The purchase token from Google Play
     * @param productId     The product ID purchased
     * @param orderId       The order ID from Google Play
     * @return Subscription response
     */
    public StaticIPSubscriptionResponse processGooglePlayPurchase(
            String purchaseToken,
            String productId,
            String orderId) {

        log.info("Processing Google Play Static IP purchase: product={}, order={}", productId, orderId);

        try {
            User user = userService.getUser();

            // Check for existing subscription
            if (staticIPService.getUserSubscription(user).isPresent()) {
                return StaticIPSubscriptionResponse.builder()
                        .success(false)
                        .message("User already has an active static IP subscription")
                        .build();
            }

            // Parse plan type from product ID
            StaticIPPlanType planType = parsePlanType(productId);
            if (planType == null) {
                log.error("Invalid Static IP product ID: {}", productId);
                return StaticIPSubscriptionResponse.builder()
                        .success(false)
                        .message("Invalid product ID: " + productId)
                        .build();
            }

            // Check for duplicate transaction
            String transactionId = orderId != null ? orderId : purchaseToken;
            if (paymentRepository.existsByPaymentIdAndGateway(transactionId, GatewayName.GOOGLE_PLAY)) {
                log.warn("Duplicate Google Play transaction for Static IP: {}", transactionId);
                return StaticIPSubscriptionResponse.builder()
                        .success(false)
                        .message("Transaction already processed")
                        .build();
            }

            // Validate purchase with Google Play Developer API
            if (validationEnabled) {
                if (!googlePlayValidationService.isAvailable()) {
                    log.error("Google Play validation service not available");
                    return StaticIPSubscriptionResponse.builder()
                            .success(false)
                            .message("Google Play validation not configured on server")
                            .build();
                }

                GooglePlayValidationService.GooglePlayValidationResult validationResult =
                        googlePlayValidationService.validateSubscription(productId, purchaseToken);

                if (!validationResult.isValid()) {
                    log.error("Google Play purchase validation failed: {}", validationResult.getErrorMessage());
                    return StaticIPSubscriptionResponse.builder()
                            .success(false)
                            .message("Purchase validation failed: " + validationResult.getErrorMessage())
                            .build();
                }

                // Check if subscription is expired
                if (validationResult.isExpired()) {
                    log.warn("Google Play subscription is expired for order: {}", orderId);
                    return StaticIPSubscriptionResponse.builder()
                            .success(false)
                            .message("Subscription has expired")
                            .build();
                }

                // Check if subscription is cancelled
                if (validationResult.isCancelled()) {
                    log.warn("Google Play subscription is cancelled for order: {}", orderId);
                    return StaticIPSubscriptionResponse.builder()
                            .success(false)
                            .message("Subscription has been cancelled")
                            .build();
                }

                // Use orderId from validation result if available
                if (validationResult.getOrderId() != null) {
                    transactionId = validationResult.getOrderId();
                }

                log.info("Google Play purchase validated successfully. Product: {}, Expires: {}, AutoRenewing: {}",
                        validationResult.getProductId(), validationResult.getExpiryTime(), validationResult.isAutoRenewing());
            } else {
                log.warn("IAP validation is disabled. Skipping Google Play validation for order: {}", orderId);
            }

            // Create payment record
            Payment payment = createPayment(user, planType, GatewayName.GOOGLE_PLAY, transactionId, purchaseToken);

            // Create subscription
            StaticIPSubscription subscription = staticIPService.createSubscription(
                    user,
                    planType,
                    true, // Auto-renew for IAP subscriptions
                    transactionId
            );

            // Update payment status
            payment.setStatus(PaymentStatus.SUCCEEDED);
            paymentRepository.save(payment);

            // Acknowledge the purchase (required by Google within 3 days)
            if (validationEnabled && googlePlayValidationService.isAvailable()) {
                boolean acknowledged = googlePlayValidationService.acknowledgeSubscription(productId, purchaseToken);
                if (!acknowledged) {
                    log.warn("Failed to acknowledge Google Play subscription, but subscription was created. " +
                            "Google may auto-refund if not acknowledged within 3 days.");
                }
            }

            log.info("Successfully created Static IP subscription via Google Play for user {}: plan={}",
                    user.getId(), planType);

            return StaticIPSubscriptionResponse.builder()
                    .success(true)
                    .message("Static IP subscription activated successfully")
                    .subscription(subscription)
                    .build();

        } catch (Exception e) {
            log.error("Error processing Google Play Static IP purchase: {}", e.getMessage(), e);
            return StaticIPSubscriptionResponse.builder()
                    .success(false)
                    .message("Failed to process purchase: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Get the correct product ID for a plan type and platform.
     * Used when initiating a purchase from the app.
     */
    public String getProductId(StaticIPPlanType planType, GatewayName gateway) {
        String prefix = gateway == GatewayName.APPLE_STORE ? "ios" : "android";
        return prefix + "_staticip_" + planType.name().toLowerCase();
    }

    /**
     * Get all available Static IP product IDs for a platform.
     */
    public Map<String, StaticIPPlanType> getAvailableProducts(GatewayName gateway) {
        String prefix = gateway == GatewayName.APPLE_STORE ? "ios_" : "android_";
        Map<String, StaticIPPlanType> products = new HashMap<>();

        for (StaticIPPlanType planType : StaticIPPlanType.values()) {
            String productId = prefix + "staticip_" + planType.name().toLowerCase();
            products.put(productId, planType);
        }

        return products;
    }

    /**
     * Parse plan type from product ID.
     */
    private StaticIPPlanType parsePlanType(String productId) {
        // First check direct mapping
        StaticIPPlanType planType = PRODUCT_TO_PLAN.get(productId.toLowerCase());
        if (planType != null) {
            return planType;
        }

        // Try to parse from pattern
        Matcher iosMatcher = IOS_PRODUCT_PATTERN.matcher(productId.toLowerCase());
        if (iosMatcher.find()) {
            String planName = iosMatcher.group(1).toUpperCase();
            try {
                return StaticIPPlanType.valueOf(planName);
            } catch (IllegalArgumentException e) {
                // Try with underscores replaced
                planName = planName.replace("_", "");
                if (planName.equals("MULTIREGION")) {
                    return StaticIPPlanType.MULTI_REGION;
                }
            }
        }

        Matcher androidMatcher = ANDROID_PRODUCT_PATTERN.matcher(productId.toLowerCase());
        if (androidMatcher.find()) {
            String planName = androidMatcher.group(1).toUpperCase();
            try {
                return StaticIPPlanType.valueOf(planName);
            } catch (IllegalArgumentException e) {
                planName = planName.replace("_", "");
                if (planName.equals("MULTIREGION")) {
                    return StaticIPPlanType.MULTI_REGION;
                }
            }
        }

        log.warn("Could not parse plan type from product ID: {}", productId);
        return null;
    }

    /**
     * Create payment record for IAP purchase.
     */
    private Payment createPayment(User user, StaticIPPlanType planType,
                                   GatewayName gateway, String transactionId, String originalToken) {
        Payment payment = Payment.builder()
                .user(user)
                .status(PaymentStatus.PENDING)
                .gateway(gateway)
                .category(PaymentCategory.BUY_CREDIT) // Static IP purchase
                .price(planType.getPriceMonthly()) // Store price for reference
                .paymentId(transactionId)
                .metaData("{\"planType\":\"" + planType.name() + "\",\"originalToken\":\"" +
                         (originalToken != null ? originalToken : "") + "\"}")
                .build();

        return paymentRepository.save(payment);
    }

    /**
     * Validate if a product ID is a valid Static IP product.
     */
    public boolean isValidStaticIPProduct(String productId) {
        return parsePlanType(productId) != null;
    }
}
