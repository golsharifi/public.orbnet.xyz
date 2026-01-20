package com.orbvpn.api.service.payment;

import com.orbvpn.api.domain.dto.PaymentResponse;
import com.orbvpn.api.domain.dto.StripePaymentResponse;
import com.orbvpn.api.domain.dto.StripeSubscriptionData;
import com.orbvpn.api.domain.dto.StripeWebhookEvent;
import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.domain.enums.PaymentStatus;
import com.orbvpn.api.repository.*;
import com.orbvpn.api.service.*;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.param.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Exception.class)
public class StripeService {

    @Value("${stripe.api.secret-key}")
    private String STRIPE_SECRET_KEY;

    // Price IDs from application.yml
    @Value("${stripe.price.basic-monthly}")
    private String PRICE_BASIC_MONTHLY;

    @Value("${stripe.price.premium-monthly}")
    private String PRICE_PREMIUM_MONTHLY;

    @Value("${stripe.price.family-monthly}")
    private String PRICE_FAMILY_MONTHLY;

    @Value("${stripe.price.basic-yearly}")
    private String PRICE_BASIC_YEARLY;

    @Value("${stripe.price.premium-yearly}")
    private String PRICE_PREMIUM_YEARLY;

    @Value("${stripe.price.family-yearly}")
    private String PRICE_FAMILY_YEARLY;

    private final StripeCustomerRepository stripeCustomerRepository;
    private final PaymentRepository paymentRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final GroupService groupService;
    private final RadiusService radiusService;
    private final AsyncNotificationHelper asyncNotificationHelper;
    private final PendingSubscriptionRepository pendingSubscriptionRepository;
    private final StripeUserService stripeUserService;
    private final StripePaymentRepository stripePaymentRepository;
    private final PaymentFulfillmentService paymentFulfillmentService;

    @PostConstruct
    public void init() {
        com.stripe.Stripe.apiKey = STRIPE_SECRET_KEY;
    }

    public StripePaymentResponse createStripePayment(Payment payment, User user, String paymentMethodId)
            throws StripeException {
        StripeCustomer stripeCustomer = getOrCreateStripeCustomer(user);

        log.info("Creating Stripe payment - isRenew: {}, groupId: {}, user: {}",
                payment.isRenew(), payment.getGroupId(), user.getEmail());

        try {
            PaymentMethod paymentMethod = attachPaymentMethodIfNeeded(
                    paymentMethodId,
                    stripeCustomer.getStripeId());

            if (payment.isRenew()) {
                return handleSubscriptionPayment(payment, stripeCustomer, paymentMethod);
            } else {
                return handleOneTimePayment(payment, stripeCustomer, paymentMethod);
            }
        } catch (StripeException e) {
            handlePaymentError(payment, e);
            throw e;
        }
    }

    public StripePaymentResponse updateSubscription(User user, String subscriptionId, int groupId)
            throws StripeException {
        log.info("Updating subscription - subscriptionId: {}, groupId: {}, userId: {}",
                subscriptionId, groupId, user.getId());

        Subscription subscription = Subscription.retrieve(subscriptionId);
        StripeCustomer stripeCustomer = stripeCustomerRepository.findByUser(user)
                .orElseThrow(() -> new NoSuchElementException("Customer not found"));

        if (!subscription.getCustomer().equals(stripeCustomer.getStripeId())) {
            log.error("Subscription {} does not belong to user {}", subscriptionId, user.getId());
            throw new RuntimeException("Subscription does not belong to user");
        }

        Group group = groupService.getById(groupId);
        String priceId = getPriceIdForGroup(group);

        try {
            SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                    .addItem(SubscriptionUpdateParams.Item.builder()
                            .setId(subscription.getItems().getData().get(0).getId())
                            .setPrice(priceId)
                            .build())
                    .setProrationBehavior(SubscriptionUpdateParams.ProrationBehavior.ALWAYS_INVOICE)
                    .putMetadata("group_id", String.valueOf(groupId))
                    .build();

            Subscription updatedSubscription = subscription.update(params);

            // Update UserSubscription
            Optional<UserSubscription> userSub = userSubscriptionRepository
                    .findByStripeSubscriptionId(subscriptionId);

            if (userSub.isPresent()) {
                UserSubscription sub = userSub.get();
                sub.setGroup(group);
                sub.setMultiLoginCount(group.getMultiLoginCount());
                sub.setExpiresAt(LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(updatedSubscription.getCurrentPeriodEnd()),
                        ZoneOffset.UTC));
                userSubscriptionRepository.save(sub);

                // Update radius records
                try {
                    radiusService.updateUserExpirationRadCheck(sub);
                } catch (Exception e) {
                    log.info("No existing radius records found, creating new ones for user {}",
                            user.getEmail());
                    radiusService.createUserRadChecks(sub);
                }
            }

            // Create response
            StripeSubscriptionData subscriptionData = new StripeSubscriptionData();
            subscriptionData.setId(updatedSubscription.getId());
            subscriptionData.setSubscriptionId(updatedSubscription.getId());
            subscriptionData.setStatus(updatedSubscription.getStatus());
            subscriptionData.setCustomerId(updatedSubscription.getCustomer());
            subscriptionData.setCurrentPeriodEnd(LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(updatedSubscription.getCurrentPeriodEnd()),
                    ZoneOffset.UTC));
            subscriptionData.setCancelAtPeriodEnd(updatedSubscription.getCancelAtPeriodEnd());
            subscriptionData.setGroupId(groupId);

            PaymentIntent intent = null;
            if (updatedSubscription.getLatestInvoiceObject() != null) {
                intent = PaymentIntent.retrieve(
                        updatedSubscription.getLatestInvoiceObject().getPaymentIntent());
            }

            return new StripePaymentResponse()
                    .setSubscriptionId(updatedSubscription.getId())
                    .setClientSecret(intent != null ? intent.getClientSecret() : null)
                    .setRequiresAction(intent != null &&
                            (intent.getStatus().equals("requires_action") ||
                                    intent.getStatus().equals("requires_source_action")))
                    .setSubscription(subscriptionData);

        } catch (StripeException e) {
            log.error("Failed to update subscription - subscriptionId: {}, error: {}",
                    subscriptionId, e.getMessage());
            throw e;
        }
    }

    public StripeSubscriptionData verifySubscription(String subscriptionId) throws StripeException {
        Subscription subscription = Subscription.retrieve(subscriptionId);

        StripeSubscriptionData stripeData = new StripeSubscriptionData();
        stripeData.setSubscriptionId(subscription.getId());
        stripeData.setStatus(subscription.getStatus());
        stripeData.setCustomerId(subscription.getCustomer());

        // Get groupId from metadata and convert to int
        String groupIdStr = subscription.getMetadata().get("group_id");
        if (groupIdStr != null) {
            stripeData.setGroupId(Integer.parseInt(groupIdStr));
        }

        // Convert expiration time
        stripeData.setExpiresAt(LocalDateTime.ofInstant(
                Instant.ofEpochSecond(subscription.getCurrentPeriodEnd()),
                ZoneOffset.UTC));

        return stripeData;
    }

    public PaymentIntent renewStripePayment(Payment payment) throws StripeException {
        // Assuming you need to create a new PaymentIntent for the renewal
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(payment.getPrice().multiply(BigDecimal.valueOf(100)).longValue()) // Set the amount
                .setCurrency("usd") // Set currency as required
                .setCustomer(payment.getUser().getStripeCustomerId()) // Customer's Stripe ID
                .build();

        return PaymentIntent.create(params);
    }

    public boolean cancelSubscription(User user, String subscriptionId) throws StripeException {
        try {
            // Retrieve the subscription from Stripe
            Subscription subscription = Subscription.retrieve(subscriptionId);

            // Cancel the subscription immediately
            Subscription canceledSubscription = subscription.cancel();

            // Check if the cancellation was successful
            return "canceled".equals(canceledSubscription.getStatus());
        } catch (StripeException e) {
            // Log and handle Stripe exceptions appropriately
            throw e;
        }
    }

    public List<StripeSubscriptionData> getUserSubscriptions(User user) throws StripeException {
        String customerId = user.getStripeCustomerId();
        if (customerId == null) {
            customerId = createStripeCustomer(user);
        }

        SubscriptionListParams params = SubscriptionListParams.builder()
                .setCustomer(customerId)
                .setLimit(10L)
                .build();

        List<StripeSubscriptionData> subscriptionsData = new ArrayList<>();

        for (Subscription subscription : Subscription.list(params).getData()) {
            StripeSubscriptionData data = new StripeSubscriptionData();
            data.setId(subscription.getId());
            data.setStatus(subscription.getStatus());
            data.setCurrentPeriodEnd(
                    LocalDateTime.ofInstant(
                            Instant.ofEpochSecond(subscription.getCurrentPeriodEnd()),
                            ZoneId.systemDefault()));
            subscriptionsData.add(data);
        }

        return subscriptionsData;
    }

    public String createStripeCustomer(User user) throws StripeException {
        CustomerCreateParams params = CustomerCreateParams.builder()
                .setEmail(user.getEmail())
                .build();

        Customer customer = Customer.create(params);
        user.setStripeCustomerId(customer.getId());
        stripeUserService.saveUser(user); // Use the new service

        return customer.getId();
    }

    private PaymentMethod attachPaymentMethodIfNeeded(String paymentMethodId, String customerId)
            throws StripeException {
        PaymentMethod paymentMethod = PaymentMethod.retrieve(paymentMethodId);
        if (!customerId.equals(paymentMethod.getCustomer())) {
            PaymentMethodAttachParams attachParams = PaymentMethodAttachParams.builder()
                    .setCustomer(customerId)
                    .build();
            paymentMethod.attach(attachParams);
            paymentMethod = PaymentMethod.retrieve(paymentMethodId);
        }
        return paymentMethod;
    }

    private StripePaymentResponse handleSubscriptionPayment(
            Payment payment,
            StripeCustomer stripeCustomer,
            PaymentMethod paymentMethod) throws StripeException {

        String priceId = getPriceIdForGroup(groupService.getById(payment.getGroupId()));

        Map<String, String> metadata = new HashMap<>();
        metadata.put("payment_id", String.valueOf(payment.getId()));
        metadata.put("group_id", String.valueOf(payment.getGroupId()));

        SubscriptionCreateParams params = SubscriptionCreateParams.builder()
                .setCustomer(stripeCustomer.getStripeId())
                .addItem(SubscriptionCreateParams.Item.builder()
                        .setPrice(priceId)
                        .build())
                .setDefaultPaymentMethod(paymentMethod.getId())
                .setPaymentBehavior(SubscriptionCreateParams.PaymentBehavior.DEFAULT_INCOMPLETE)
                .setMetadata(metadata)
                .build();

        Subscription subscription = Subscription.create(params);

        // Get the latest invoice and payment details
        com.stripe.model.Invoice invoice = com.stripe.model.Invoice.retrieve(subscription.getLatestInvoice());
        String paymentIntentId = invoice.getPaymentIntent();
        String latestInvoiceId = subscription.getLatestInvoice();

        // Store pending subscription
        createPendingSubscription(payment, subscription.getId());

        // Save payment intent ID to payment
        payment.setPaymentId(paymentIntentId);
        paymentRepository.save(payment);

        // Create StripePayment record with all available details
        StripePayment stripePayment = StripePayment.builder()
                .payment(payment)
                .stripeCustomer(stripeCustomer)
                .paymentIntentId(paymentIntentId)
                .paymentMethodId(paymentMethod.getId())
                .subscriptionId(subscription.getId())
                .invoiceId(latestInvoiceId)
                .latestInvoiceId(latestInvoiceId)
                .subscriptionStatus(subscription.getStatus())
                .stripeStatus(subscription.getStatus())
                .currentPeriodStart(LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(subscription.getCurrentPeriodStart()),
                        ZoneOffset.UTC))
                .currentPeriodEnd(LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(subscription.getCurrentPeriodEnd()),
                        ZoneOffset.UTC))
                .cancelAtPeriodEnd(subscription.getCancelAtPeriodEnd())
                .build();

        stripePaymentRepository.save(stripePayment);

        return createSubscriptionResponse(subscription, payment, paymentIntentId);
    }

    private StripePaymentResponse handleOneTimePayment(
            Payment payment,
            StripeCustomer stripeCustomer,
            PaymentMethod paymentMethod) throws StripeException {

        BigDecimal amount = payment.getPrice().multiply(new BigDecimal(100));

        Map<String, String> metadata = new HashMap<>();
        metadata.put("payment_id", String.valueOf(payment.getId()));
        metadata.put("group_id", String.valueOf(payment.getGroupId()));

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setCurrency("usd")
                .setAmount(amount.longValue())
                .setPaymentMethod(paymentMethod.getId())
                .setCustomer(stripeCustomer.getStripeId())
                .setConfirm(true)
                .setSetupFutureUsage(PaymentIntentCreateParams.SetupFutureUsage.OFF_SESSION)
                .putAllMetadata(metadata)
                .build();

        PaymentIntent intent = PaymentIntent.create(params);
        if (intent == null || intent.getId() == null) {
            log.error("Failed to create PaymentIntent for payment: {}", payment.getId());
            throw new RuntimeException("Failed to create PaymentIntent");

        }
        payment.setPaymentId(intent.getId());
        paymentRepository.save(payment);

        return createPaymentResponse(intent, payment);
    }

    @Transactional(rollbackFor = Exception.class)
    public PaymentIntent confirmPaymentIntent(String paymentIntentId) throws StripeException {
        PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);

        if ("succeeded".equals(intent.getStatus())) {
            updatePaymentStatus(intent);
            return intent;
        }

        PaymentIntentConfirmParams params = PaymentIntentConfirmParams.builder()
                .setPaymentMethod(intent.getPaymentMethod())
                .build();

        PaymentIntent confirmedIntent = intent.confirm(params);

        // Update stripe payment record first
        Optional<StripePayment> stripePaymentOpt = stripePaymentRepository.findByPaymentIntentId(paymentIntentId);
        if (stripePaymentOpt.isPresent()) {
            StripePayment stripePayment = stripePaymentOpt.get();
            stripePayment.setStripeStatus(confirmedIntent.getStatus());
            if (confirmedIntent.getStatus().equals("succeeded")) {
                stripePayment.setSubscriptionStatus("active");
            }
            stripePaymentRepository.saveAndFlush(stripePayment);
        }

        // Then update payment status
        updatePaymentStatus(confirmedIntent);

        return confirmedIntent;
    }

    private void updatePaymentStatus(PaymentIntent intent) {
        Optional<Payment> paymentOpt = paymentRepository.findByPaymentId(intent.getId());
        if (paymentOpt.isPresent()) {
            Payment payment = paymentOpt.get();
            if ("succeeded".equals(intent.getStatus())) {
                try {
                    paymentFulfillmentService.fulfillPayment(payment);
                } catch (Exception e) {
                    log.error("Error fulfilling payment: {}", e.getMessage());
                    if (!e.getMessage().contains("Payment is already fulfilled")) {
                        throw e;
                    }
                }
            } else {
                payment.setStatus(PaymentStatus.FAILED);
                paymentRepository.saveAndFlush(payment);
            }
        }
    }

    @Transactional
    public void handleWebhookEvent(StripeWebhookEvent event) {
        log.info("Processing Stripe webhook event: {}", event.getType());

        try {
            switch (event.getType()) {
                case "customer.subscription.created":
                    handleSubscriptionCreated(event);
                    break;

                case "customer.subscription.updated":
                    handleSubscriptionUpdated(event);
                    break;

                case "customer.subscription.deleted":
                    handleSubscriptionDeleted(event);
                    break;

                case "invoice.payment_succeeded":
                    handlePaymentSucceeded(event);
                    break;

                case "invoice.payment_failed":
                    handlePaymentFailed(event);
                    break;

                default:
                    log.info("Unhandled event type: {}", event.getType());
            }
        } catch (Exception e) {
            log.error("Error processing webhook event: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process webhook event", e);
        }
    }

    private void handleSubscriptionCreated(StripeWebhookEvent event) {
        log.info("Processing subscription created event for subscription ID: {}", event.getSubscriptionId());

        PendingSubscription pending = pendingSubscriptionRepository
                .findBySubscriptionId(event.getSubscriptionId())
                .orElse(null);

        if (pending == null) {
            log.warn("No pending subscription found for ID: {}. Attempting to find through payment intent.",
                    event.getSubscriptionId());

            // Try to find through payment intent
            if (event.getPaymentIntentId() != null) {
                Optional<Payment> payment = paymentRepository.findByPaymentId(event.getPaymentIntentId());
                Optional<StripePayment> stripePayment = stripePaymentRepository
                        .findByPaymentIntentId(event.getPaymentIntentId());

                if (payment.isPresent() && stripePayment.isPresent()) {
                    try {
                        UserSubscription subscription = new UserSubscription();
                        subscription.setUser(payment.get().getUser());
                        subscription.setGroup(groupService.getById(payment.get().getGroupId()));
                        subscription.setPayment(payment.get());
                        subscription.setStripeSubscriptionId(event.getSubscriptionId());
                        subscription.setStripeCustomerId(stripePayment.get().getStripeCustomer().getStripeId());
                        subscription.setExpiresAt(event.getExpiresAt());
                        subscription.setMultiLoginCount(subscription.getGroup().getMultiLoginCount());
                        subscription.setCanceled(false);
                        subscription.setAutoRenew(true);
                        subscription.setDuration(subscription.getGroup().getDuration());
                        subscription.setPrice(payment.get().getPrice());

                        userSubscriptionRepository.save(subscription);

                        // Update Radius service
                        try {
                            radiusService.updateUserExpirationRadCheck(subscription);
                        } catch (Exception e) {
                            log.error("Failed to update radius records: {}", e.getMessage());
                        }

                        asyncNotificationHelper.sendSubscriptionWebhookAsync(subscription, "SUBSCRIPTION_CREATED");

                        log.info("Successfully created subscription from payment intent: {}",
                                event.getPaymentIntentId());
                        return;
                    } catch (Exception e) {
                        log.error("Error creating subscription from payment intent: {}", e.getMessage());
                        throw new RuntimeException("Failed to create subscription", e);
                    }
                }
            }
            return;
        }

        try {
            log.info("Creating subscription from pending subscription record");
            UserSubscription subscription = createSubscriptionFromPending(pending);
            userSubscriptionRepository.save(subscription);

            // Update Radius service
            try {
                radiusService.updateUserExpirationRadCheck(subscription);
            } catch (Exception e) {
                log.error("Failed to update radius records: {}", e.getMessage());
            }

            asyncNotificationHelper.sendSubscriptionWebhookAsync(subscription, "SUBSCRIPTION_CREATED");

            pendingSubscriptionRepository.delete(pending);
            log.info("Successfully created and saved subscription from pending record");
        } catch (Exception e) {
            log.error("Error creating subscription from pending: {}", e.getMessage());
            throw new RuntimeException("Failed to create subscription", e);
        }
    }

    private void handleSubscriptionUpdated(StripeWebhookEvent event) {
        userSubscriptionRepository.findByStripeSubscriptionId(event.getSubscriptionId())
                .ifPresent(subscription -> {
                    subscription.setExpiresAt(event.getExpiresAt());
                    subscription.setCanceled(false);
                    userSubscriptionRepository.save(subscription);

                    try {
                        radiusService.updateUserExpirationRadCheck(subscription);
                    } catch (Exception e) {
                        log.error("Failed to update radius records: {}", e.getMessage());
                    }

                    asyncNotificationHelper.sendSubscriptionWebhookAsync(subscription, "SUBSCRIPTION_UPDATED");
                });
    }

    private void handleSubscriptionDeleted(StripeWebhookEvent event) {
        userSubscriptionRepository.findByStripeSubscriptionId(event.getSubscriptionId())
                .ifPresent(subscription -> {
                    subscription.setCanceled(true);
                    userSubscriptionRepository.save(subscription);

                    try {
                        radiusService.updateUserExpirationRadCheck(subscription);
                    } catch (Exception e) {
                        log.error("Failed to update radius records: {}", e.getMessage());
                    }

                    asyncNotificationHelper.sendSubscriptionWebhookAsync(subscription, "SUBSCRIPTION_DELETED");
                });
    }

    private void handlePaymentSucceeded(StripeWebhookEvent event) {
        log.info("Processing payment succeeded event: {}", event);

        if (event.getSubscriptionId() != null) {
            userSubscriptionRepository.findByStripeSubscriptionId(event.getSubscriptionId())
                    .ifPresent(subscription -> {
                        subscription.setExpiresAt(event.getExpiresAt());
                        subscription.setCanceled(false);
                        userSubscriptionRepository.save(subscription);

                        try {
                            radiusService.updateUserExpirationRadCheck(subscription);
                        } catch (Exception e) {
                            log.error("Failed to update radius records: {}", e.getMessage());
                        }
                    });
        }

        // Update payment status and fulfill payment if found
        if (event.getPaymentIntentId() != null) {
            try {
                paymentRepository.findByPaymentId(event.getPaymentIntentId())
                        .ifPresent(payment -> paymentFulfillmentService.fulfillPayment(payment));
            } catch (Exception e) {
                log.error("Error in payment fulfillment: {}", e.getMessage());
            }
        }
    }

    private void handlePaymentFailed(StripeWebhookEvent event) {
        if (event.getSubscriptionId() != null) {
            userSubscriptionRepository.findByStripeSubscriptionId(event.getSubscriptionId())
                    .ifPresent(subscription -> {
                        log.warn("Payment failed for subscription: {}", subscription.getId());
                        // Optionally mark subscription as problematic
                    });
        }

        // Update payment status if found
        if (event.getPaymentIntentId() != null) {
            paymentRepository.findByPaymentId(event.getPaymentIntentId())
                    .ifPresent(payment -> {
                        payment.setStatus(PaymentStatus.FAILED);
                        payment.setErrorMessage(event.getError());
                        paymentRepository.save(payment);
                    });
        }
    }

    // Helper methods
    private StripeCustomer getOrCreateStripeCustomer(User user) throws StripeException {
        return stripeCustomerRepository.findByUser(user)
                .orElseGet(() -> {
                    try {
                        CustomerCreateParams params = CustomerCreateParams.builder()
                                .setEmail(user.getEmail())
                                .setName(user.getUsername())
                                .build();

                        Customer customer = Customer.create(params);

                        StripeCustomer stripeCustomer = new StripeCustomer();
                        stripeCustomer.setUser(user);
                        stripeCustomer.setStripeId(customer.getId());
                        return stripeUserService.saveStripeCustomer(stripeCustomer); // Use the new service
                    } catch (StripeException e) {
                        throw new RuntimeException("Failed to create Stripe customer", e);
                    }
                });
    }

    private void createPendingSubscription(Payment payment, String subscriptionId) {
        PendingSubscription pending = PendingSubscription.builder()
                .paymentId(payment.getId())
                .subscriptionId(subscriptionId)
                .userId(payment.getUser().getId())
                .groupId(payment.getGroupId())
                .createdAt(LocalDateTime.now())
                .build();

        pendingSubscriptionRepository.save(pending);
    }

    private UserSubscription createSubscriptionFromPending(PendingSubscription pending) {
        // Convert the ID to Integer when finding payment
        Payment payment = paymentRepository.findById(pending.getPaymentId().intValue()) // Convert Long to Integer
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        User user = payment.getUser();
        Group group = groupService.getById(pending.getGroupId());

        UserSubscription subscription = new UserSubscription();
        subscription.setUser(user);
        subscription.setGroup(group);
        subscription.setPayment(payment);
        subscription.setStripeSubscriptionId(pending.getSubscriptionId());
        subscription.setStripeCustomerId(payment.getUser().getStripeCustomer().getStripeId());
        subscription.setExpiresAt(LocalDateTime.now().plusDays(group.getDuration()));
        subscription.setMultiLoginCount(group.getMultiLoginCount());
        subscription.setCanceled(false);
        subscription.setAutoRenew(true);
        subscription.setDuration(group.getDuration());
        subscription.setPrice(payment.getPrice());

        return subscription;
    }

    private String getPriceIdForGroup(Group group) {
        switch (group.getId()) {
            case 21:
                return PRICE_BASIC_MONTHLY;
            case 22:
                return PRICE_PREMIUM_MONTHLY;
            case 23:
                return PRICE_FAMILY_MONTHLY;
            case 24:
                return PRICE_BASIC_YEARLY;
            case 25:
                return PRICE_PREMIUM_YEARLY;
            case 26:
                return PRICE_FAMILY_YEARLY;
            default:
                throw new IllegalArgumentException("No Stripe price ID mapped for group: " + group.getId());
        }
    }

    private void handlePaymentError(Payment payment, StripeException e) {
        log.error("Stripe payment failed", e);
        payment.setStatus(PaymentStatus.FAILED);
        payment.setErrorMessage(e.getMessage());
        paymentRepository.save(payment);
    }

    private StripePaymentResponse createSubscriptionResponse(
            Subscription subscription,
            Payment payment,
            String paymentIntentId) {

        PaymentIntent intent = null;
        try {
            if (paymentIntentId != null) {
                intent = PaymentIntent.retrieve(paymentIntentId);
            }
        } catch (StripeException e) {
            log.error("Error retrieving payment intent", e);
        }

        StripeSubscriptionData subscriptionData = new StripeSubscriptionData();
        subscriptionData.setId(subscription.getId());
        subscriptionData.setSubscriptionId(subscription.getId());
        subscriptionData.setStatus(subscription.getStatus());
        subscriptionData.setCustomerId(payment.getUser().getStripeCustomer().getStripeId());
        subscriptionData.setCurrentPeriodEnd(LocalDateTime.ofInstant(
                Instant.ofEpochSecond(subscription.getCurrentPeriodEnd()),
                ZoneOffset.UTC));
        subscriptionData.setCancelAtPeriodEnd(subscription.getCancelAtPeriodEnd());
        subscriptionData.setGroupId(payment.getGroupId());

        return new StripePaymentResponse()
                .setSubscriptionId(subscription.getId())
                .setPaymentIntentId(paymentIntentId) // Set the payment intent ID
                .setClientSecret(intent != null ? intent.getClientSecret() : null)
                .setRequiresAction(intent != null &&
                        (intent.getStatus().equals("requires_action") ||
                                intent.getStatus().equals("requires_source_action")))
                .setSubscription(subscriptionData);
    }

    private StripePaymentResponse createPaymentResponse(PaymentIntent intent, Payment payment) {

        if (intent == null) {
            log.error("PaymentIntent is null for payment: {}", payment.getId());
            throw new IllegalArgumentException("PaymentIntent cannot be null");
        }

        String paymentIntentId = intent.getId();
        if (paymentIntentId == null || paymentIntentId.isEmpty()) {
            log.error("PaymentIntent ID is null or empty for payment: {}", payment.getId());
            throw new IllegalArgumentException("PaymentIntent ID cannot be null or empty");
        }

        StripePaymentResponse response = new StripePaymentResponse()
                .setPaymentIntentId(intent.getId())
                .setClientSecret(intent.getClientSecret());

        switch (intent.getStatus()) {
            case "requires_action":
            case "requires_source_action":
                response.setRequiresAction(true);
                break;
            case "requires_payment_method":
                response.setError("Payment failed. Please try another payment method.");
                payment.setStatus(PaymentStatus.FAILED);
                break;
            case "succeeded":
                response.setRequiresAction(false);
                payment.setStatus(PaymentStatus.SUCCEEDED);
                break;
            default:
                response.setError("Unexpected payment status: " + intent.getStatus());
                payment.setStatus(PaymentStatus.FAILED);
        }

        // Ensure the payment ID is set
        if (payment.getPaymentId() == null) {
            payment.setPaymentId(paymentIntentId);
        }

        paymentRepository.save(payment);
        return response;
    }

    public PaymentResponse createPaymentIntent(Payment payment) throws StripeException {
        PaymentIntent intent = PaymentIntent.create(PaymentIntentCreateParams.builder()
                .setAmount(payment.getPrice().multiply(BigDecimal.valueOf(100)).longValue())
                .setCurrency("usd")
                .setCustomer(payment.getUser().getStripeCustomerId())
                .build());

        return PaymentResponse.builder()
                .success(true)
                .paymentId(intent.getId())
                .clientSecret(intent.getClientSecret())
                .amount(payment.getPrice().doubleValue())
                .build();
    }

}