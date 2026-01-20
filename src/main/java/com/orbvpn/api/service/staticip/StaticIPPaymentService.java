package com.orbvpn.api.service.staticip;

import com.orbvpn.api.domain.dto.PaymentResponse;
import com.orbvpn.api.domain.dto.staticip.StaticIPSubscriptionResponse;
import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.domain.enums.*;
import com.orbvpn.api.repository.PaymentRepository;
import com.orbvpn.api.service.payment.StripeService;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Payment service for Static IP subscriptions.
 * Handles payment processing via Stripe and other gateways.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StaticIPPaymentService {

    private final StaticIPService staticIPService;
    private final PaymentRepository paymentRepository;

    @Value("${stripe.api.secret-key:}")
    private String stripeSecretKey;

    // Price IDs for Static IP plans (would be configured in application.yml)
    private static final Map<StaticIPPlanType, String> STRIPE_PRICE_IDS = new HashMap<>();
    static {
        // These would be actual Stripe price IDs in production
        STRIPE_PRICE_IDS.put(StaticIPPlanType.PERSONAL, "price_staticip_personal");
        STRIPE_PRICE_IDS.put(StaticIPPlanType.PRO, "price_staticip_pro");
        STRIPE_PRICE_IDS.put(StaticIPPlanType.MULTI_REGION, "price_staticip_multiregion");
        STRIPE_PRICE_IDS.put(StaticIPPlanType.BUSINESS, "price_staticip_business");
        STRIPE_PRICE_IDS.put(StaticIPPlanType.ENTERPRISE, "price_staticip_enterprise");
    }

    /**
     * Process Static IP subscription with payment.
     * Returns a payment URL or creates subscription directly for test mode.
     */
    @Transactional
    public StaticIPSubscriptionResponse processSubscriptionWithPayment(
            User user,
            StaticIPPlanType planType,
            String paymentMethod,
            String selectedCoin,
            boolean autoRenew) {

        log.info("Processing Static IP subscription payment - user: {}, plan: {}, method: {}",
                user.getEmail(), planType, paymentMethod);

        try {
            // Check for existing subscription
            if (staticIPService.getUserSubscription(user).isPresent()) {
                return StaticIPSubscriptionResponse.builder()
                        .success(false)
                        .message("User already has an active static IP subscription")
                        .build();
            }

            // Handle different payment methods
            GatewayName gateway = parseGateway(paymentMethod);

            switch (gateway) {
                case STRIPE:
                    return processStripePayment(user, planType, autoRenew);

                case APPLE_STORE:
                case GOOGLE_PLAY:
                    // For mobile payments, return product ID for in-app purchase
                    return StaticIPSubscriptionResponse.builder()
                            .success(true)
                            .message("Use in-app purchase to complete subscription. After purchase, call verifyStaticIP" +
                                    (gateway == GatewayName.APPLE_STORE ? "ApplePurchase" : "GooglePlayPurchase") +
                                    " mutation to activate.")
                            .mobileProductId(getInAppProductId(planType, gateway))
                            .build();

                case COIN_PAYMENT:
                case NOW_PAYMENT:
                    return processCryptoPayment(user, planType, gateway, selectedCoin, autoRenew);

                case GIFT_CARD:
                case FREE:
                    // For test/gift card/free, create subscription directly
                    return createSubscriptionDirectly(user, planType, autoRenew, paymentMethod);

                default:
                    return StaticIPSubscriptionResponse.builder()
                            .success(false)
                            .message("Unsupported payment method: " + paymentMethod)
                            .build();
            }

        } catch (Exception e) {
            log.error("Error processing Static IP payment: {}", e.getMessage(), e);
            return StaticIPSubscriptionResponse.builder()
                    .success(false)
                    .message("Payment processing failed: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Process Stripe payment for Static IP subscription.
     */
    private StaticIPSubscriptionResponse processStripePayment(
            User user, StaticIPPlanType planType, boolean autoRenew) throws StripeException {

        if (stripeSecretKey == null || stripeSecretKey.isEmpty()) {
            log.warn("Stripe not configured, falling back to direct subscription creation");
            return createSubscriptionDirectly(user, planType, autoRenew, "STRIPE_TEST");
        }

        com.stripe.Stripe.apiKey = stripeSecretKey;

        BigDecimal price = planType.getPriceMonthly();
        long amountInCents = price.multiply(BigDecimal.valueOf(100)).longValue();

        // Create a PaymentIntent
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency("usd")
                .setDescription("Static IP Subscription - " + planType.getDisplayName())
                .putMetadata("user_id", String.valueOf(user.getId()))
                .putMetadata("plan_type", planType.name())
                .putMetadata("product_type", "STATIC_IP")
                .build();

        PaymentIntent paymentIntent = PaymentIntent.create(params);

        // Save pending payment record
        Payment payment = Payment.builder()
                .user(user)
                .status(PaymentStatus.PENDING)
                .gateway(GatewayName.STRIPE)
                .category(PaymentCategory.BUY_CREDIT) // Static IP purchase
                .price(price)
                .build();
        payment = paymentRepository.save(payment);

        log.info("Created Stripe PaymentIntent {} for Static IP subscription", paymentIntent.getId());

        return StaticIPSubscriptionResponse.builder()
                .success(true)
                .message("Complete payment to activate subscription")
                .paymentUrl(paymentIntent.getClientSecret())
                .build();
    }

    /**
     * Process crypto payment for Static IP subscription.
     */
    private StaticIPSubscriptionResponse processCryptoPayment(
            User user, StaticIPPlanType planType, GatewayName gateway,
            String selectedCoin, boolean autoRenew) {

        // TODO: Integrate with CoinPayment or NowPayment service
        // For now, return a placeholder response
        log.info("Crypto payment requested for Static IP - gateway: {}, coin: {}", gateway, selectedCoin);

        return StaticIPSubscriptionResponse.builder()
                .success(true)
                .message("Crypto payment initiated. Complete payment to activate subscription.")
                .paymentUrl("crypto_payment_url_placeholder")
                .build();
    }

    /**
     * Create subscription directly without payment (for test/gift mode).
     */
    private StaticIPSubscriptionResponse createSubscriptionDirectly(
            User user, StaticIPPlanType planType, boolean autoRenew, String externalId) {

        log.info("Creating Static IP subscription directly (no payment) for user {}", user.getEmail());

        StaticIPSubscription subscription = staticIPService.createSubscription(
                user, planType, autoRenew, externalId);

        return StaticIPSubscriptionResponse.builder()
                .success(true)
                .message("Static IP subscription created successfully")
                .subscription(subscription)
                .build();
    }

    /**
     * Fulfill a completed payment and create the subscription.
     * Called by webhook handlers when payment is confirmed.
     */
    @Transactional
    public StaticIPSubscription fulfillPayment(Payment payment, StaticIPPlanType planType, boolean autoRenew) {
        log.info("Fulfilling Static IP payment {} for user {}", payment.getId(), payment.getUser().getEmail());

        // Update payment status
        payment.setStatus(PaymentStatus.SUCCEEDED);
        paymentRepository.save(payment);

        // Create the subscription
        return staticIPService.createSubscription(
                payment.getUser(),
                planType,
                autoRenew,
                "payment-" + payment.getId()
        );
    }

    /**
     * Get in-app product ID for mobile purchases.
     */
    private String getInAppProductId(StaticIPPlanType planType, GatewayName gateway) {
        String prefix = gateway == GatewayName.APPLE_STORE ? "ios" : "android";
        return prefix + "_staticip_" + planType.name().toLowerCase();
    }

    /**
     * Parse gateway name from payment method string.
     */
    private GatewayName parseGateway(String paymentMethod) {
        if (paymentMethod == null || paymentMethod.isEmpty()) {
            return GatewayName.STRIPE; // Default
        }

        try {
            return GatewayName.valueOf(paymentMethod.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Check for common aliases
            switch (paymentMethod.toLowerCase()) {
                case "card":
                case "credit_card":
                    return GatewayName.STRIPE;
                case "apple":
                case "apple_pay":
                    return GatewayName.APPLE_STORE;
                case "google":
                case "google_pay":
                    return GatewayName.GOOGLE_PLAY;
                case "crypto":
                case "bitcoin":
                    return GatewayName.COIN_PAYMENT;
                case "test":
                case "free":
                    return GatewayName.FREE;
                default:
                    log.warn("Unknown payment method: {}, defaulting to FREE", paymentMethod);
                    return GatewayName.FREE;
            }
        }
    }
}
