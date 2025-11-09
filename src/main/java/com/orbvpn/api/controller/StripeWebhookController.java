package com.orbvpn.api.controller;

import com.orbvpn.api.domain.dto.StripeWebhookEvent;
import com.orbvpn.api.service.subscription.RenewUserSubscriptionService;
import com.orbvpn.api.service.payment.StripeService;
import com.stripe.model.*;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/webhooks/stripe")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final RenewUserSubscriptionService renewUserSubscriptionService;
    private final StripeService stripeService;

    @Value("${stripe.api.webhook-secret}")
    private String webhookSecret;

    private static final Map<String, String> EVENT_DESCRIPTIONS = new HashMap<>();
    static {
        EVENT_DESCRIPTIONS.put("customer.subscription.created", "New subscription created");
        EVENT_DESCRIPTIONS.put("customer.subscription.updated", "Subscription updated");
        EVENT_DESCRIPTIONS.put("customer.subscription.deleted", "Subscription cancelled");
        EVENT_DESCRIPTIONS.put("invoice.payment_succeeded", "Payment succeeded");
        EVENT_DESCRIPTIONS.put("invoice.payment_failed", "Payment failed");
        EVENT_DESCRIPTIONS.put("payment_intent.succeeded", "Payment completed successfully");
        EVENT_DESCRIPTIONS.put("payment_intent.payment_failed", "Payment attempt failed");
        EVENT_DESCRIPTIONS.put("charge.succeeded", "Charge successful");
        EVENT_DESCRIPTIONS.put("charge.failed", "Charge failed");
    }

    @PostMapping
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        log.info("Received Stripe webhook. Processing...");

        try {
            // Verify webhook signature
            Event event = verifyWebhookSignature(payload, sigHeader);

            // Log the event type
            log.info("Processing Stripe event: {} - {}",
                    event.getType(),
                    EVENT_DESCRIPTIONS.getOrDefault(event.getType(), "Unknown event type"));

            // Convert and process the event
            StripeWebhookEvent webhookEvent = convertStripeEvent(event);
            processWebhookEvent(webhookEvent);

            log.info("Successfully processed Stripe webhook event: {}", event.getType());
            return ResponseEntity.ok("Webhook processed successfully");

        } catch (Exception e) {
            log.error("Error processing Stripe webhook: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("Webhook processing failed: " + e.getMessage());
        }
    }

    private Event verifyWebhookSignature(String payload, String sigHeader) {
        try {
            return Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (Exception e) {
            log.error("Invalid webhook signature for event. Signature: {}, Payload: {}", sigHeader, payload, e);
            throw new IllegalArgumentException("Invalid webhook signature");
        }
    }

    private void processWebhookEvent(StripeWebhookEvent webhookEvent) {
        try {
            // First, attempt to process with StripeService
            stripeService.handleWebhookEvent(webhookEvent);

            // Then, process subscription-related events
            renewUserSubscriptionService.handleStripeNotification(webhookEvent);

        } catch (Exception e) {
            log.error("Error processing webhook event", e);
            throw new RuntimeException("Failed to process webhook event", e);
        }
    }

    private StripeWebhookEvent convertStripeEvent(Event event) {
        StripeWebhookEvent webhookEvent = new StripeWebhookEvent();
        webhookEvent.setType(event.getType());

        try {
            switch (event.getType()) {
                case "customer.subscription.created":
                case "customer.subscription.updated":
                case "customer.subscription.deleted":
                    handleSubscriptionEvent(event, webhookEvent);
                    break;

                case "invoice.payment_succeeded":
                case "invoice.payment_failed":
                    handleInvoiceEvent(event, webhookEvent);
                    break;

                case "payment_intent.succeeded":
                case "payment_intent.payment_failed":
                    handlePaymentIntentEvent(event, webhookEvent);
                    break;

                case "charge.succeeded":
                case "charge.failed":
                    handleChargeEvent(event, webhookEvent);
                    break;

                default:
                    log.warn("Unhandled event type: {}", event.getType());
                    handleGenericEvent(event, webhookEvent);
            }
        } catch (Exception e) {
            log.error("Error converting Stripe event: {}", e.getMessage(), e);
            throw new RuntimeException("Error processing Stripe webhook event", e);
        }

        return webhookEvent;
    }

    private void handleSubscriptionEvent(Event event, StripeWebhookEvent webhookEvent) {
        Subscription subscription = (Subscription) event.getDataObjectDeserializer().getObject().get();

        webhookEvent.setSubscriptionId(subscription.getId());
        webhookEvent.setCustomerId(subscription.getCustomer());
        webhookEvent.setStatus(subscription.getStatus());

        if (!subscription.getItems().getData().isEmpty()) {
            Price price = subscription.getItems().getData().get(0).getPrice();
            webhookEvent.setPriceId(price.getId());
            webhookEvent.setProductId(price.getProduct());
            webhookEvent.setInterval(price.getRecurring().getInterval());
            webhookEvent.setIntervalCount(price.getRecurring().getIntervalCount());
        }

        webhookEvent.setCancelAtPeriodEnd(subscription.getCancelAtPeriodEnd());

        if (subscription.getTrialEnd() != null) {
            webhookEvent.setTrialEnd(convertToDateTime(subscription.getTrialEnd()));
            webhookEvent.setIsTrialPeriod(true);
        }

        if (subscription.getCurrentPeriodEnd() != null) {
            webhookEvent.setExpiresAt(convertToDateTime(subscription.getCurrentPeriodEnd()));
        }
    }

    private void handleInvoiceEvent(Event event, StripeWebhookEvent webhookEvent) {
        Invoice invoice = (Invoice) event.getDataObjectDeserializer().getObject().get();

        webhookEvent.setInvoiceId(invoice.getId());
        webhookEvent.setSubscriptionId(invoice.getSubscription());
        webhookEvent.setCustomerId(invoice.getCustomer());
        webhookEvent.setAmount(invoice.getAmountPaid());
        webhookEvent.setCurrency(invoice.getCurrency());
        webhookEvent.setPaid(invoice.getPaid());
        webhookEvent.setCustomerEmail(invoice.getCustomerEmail());

        if (invoice.getPaymentIntent() != null) {
            webhookEvent.setPaymentIntentId(invoice.getPaymentIntent());
        }
    }

    private void handlePaymentIntentEvent(Event event, StripeWebhookEvent webhookEvent) {
        PaymentIntent paymentIntent = (PaymentIntent) event.getDataObjectDeserializer().getObject().get();

        webhookEvent.setPaymentIntentId(paymentIntent.getId());
        webhookEvent.setCustomerId(paymentIntent.getCustomer());
        webhookEvent.setAmount(paymentIntent.getAmount());
        webhookEvent.setCurrency(paymentIntent.getCurrency());
        webhookEvent.setStatus(paymentIntent.getStatus());

        if (paymentIntent.getPaymentMethod() != null) {
            webhookEvent.setPaymentMethodId(paymentIntent.getPaymentMethod());
        }

        if (paymentIntent.getLastPaymentError() != null) {
            webhookEvent.setError(paymentIntent.getLastPaymentError().getMessage());
        }
    }

    private void handleChargeEvent(Event event, StripeWebhookEvent webhookEvent) {
        Charge charge = (Charge) event.getDataObjectDeserializer().getObject().get();

        webhookEvent.setAmount(charge.getAmount());
        webhookEvent.setCurrency(charge.getCurrency());
        webhookEvent.setPaid(charge.getPaid());
        webhookEvent.setCustomerId(charge.getCustomer());

        if (charge.getPaymentIntent() != null) {
            webhookEvent.setPaymentIntentId(charge.getPaymentIntent());
        }
    }

    private void handleGenericEvent(Event event, StripeWebhookEvent webhookEvent) {
        // Handle any common fields or metadata that might be useful
        webhookEvent.setType(event.getType());
        if (event.getAccount() != null) {
            log.info("Event received for Stripe account: {}", event.getAccount());
        }
    }

    private LocalDateTime convertToDateTime(Long epochSeconds) {
        return epochSeconds == null ? null
                : LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC);
    }
}