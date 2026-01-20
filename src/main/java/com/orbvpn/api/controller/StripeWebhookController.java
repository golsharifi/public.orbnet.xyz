package com.orbvpn.api.controller;

import com.orbvpn.api.domain.dto.StripeWebhookEvent;
import com.orbvpn.api.domain.entity.ProcessedStripeWebhookEvent;
import com.orbvpn.api.repository.ProcessedStripeWebhookEventRepository;
import com.orbvpn.api.service.subscription.notification.StripeNotificationProcessor;
import com.stripe.model.*;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
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

    private final StripeNotificationProcessor stripeNotificationProcessor;
    private final ProcessedStripeWebhookEventRepository processedEventRepository;

    @Value("${stripe.api.webhook-secret}")
    private String webhookSecret;

    private static final Map<String, String> EVENT_DESCRIPTIONS = new HashMap<>();
    static {
        EVENT_DESCRIPTIONS.put("customer.subscription.created", "New subscription created");
        EVENT_DESCRIPTIONS.put("customer.subscription.updated", "Subscription updated");
        EVENT_DESCRIPTIONS.put("customer.subscription.deleted", "Subscription cancelled");
        EVENT_DESCRIPTIONS.put("customer.subscription.trial_will_end", "Trial ending soon");
        EVENT_DESCRIPTIONS.put("invoice.payment_succeeded", "Payment succeeded");
        EVENT_DESCRIPTIONS.put("invoice.payment_failed", "Payment failed");
        EVENT_DESCRIPTIONS.put("invoice.created", "Invoice created");
        EVENT_DESCRIPTIONS.put("invoice.finalized", "Invoice finalized");
        EVENT_DESCRIPTIONS.put("payment_intent.succeeded", "Payment completed successfully");
        EVENT_DESCRIPTIONS.put("payment_intent.payment_failed", "Payment attempt failed");
        EVENT_DESCRIPTIONS.put("charge.succeeded", "Charge successful");
        EVENT_DESCRIPTIONS.put("charge.failed", "Charge failed");
        EVENT_DESCRIPTIONS.put("charge.refunded", "Charge refunded");
        EVENT_DESCRIPTIONS.put("charge.dispute.created", "Dispute created");
    }

    @PostMapping
    @Transactional
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        log.info("Received Stripe webhook. Processing...");

        Event event;
        try {
            // Verify webhook signature
            event = verifyWebhookSignature(payload, sigHeader);
        } catch (IllegalArgumentException e) {
            // Invalid signature - return 400 to tell Stripe the payload was bad
            log.error("Invalid webhook signature: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid webhook signature");
        }

        String eventId = event.getId();

        // Check for duplicate event (idempotency)
        if (processedEventRepository.existsByEventId(eventId)) {
            log.info("Duplicate Stripe event detected, eventId: {}. Acknowledging.", eventId);
            return ResponseEntity.ok("Duplicate event acknowledged");
        }

        // Record that we're processing this event
        ProcessedStripeWebhookEvent processedEvent = new ProcessedStripeWebhookEvent(eventId);
        processedEvent.setEventType(event.getType());

        try {
            log.info("Processing Stripe event: {} (ID: {}) - {}",
                    event.getType(),
                    eventId,
                    EVENT_DESCRIPTIONS.getOrDefault(event.getType(), "Unknown event type"));

            // Convert and process the event
            StripeWebhookEvent webhookEvent = convertStripeEvent(event);

            // Record event details
            processedEvent.setSubscriptionId(webhookEvent.getSubscriptionId());
            processedEvent.setCustomerId(webhookEvent.getCustomerId());
            processedEvent.setPaymentIntentId(webhookEvent.getPaymentIntentId());

            // Process the event through a single handler (no duplicate processing)
            stripeNotificationProcessor.processNotification(webhookEvent);

            // Mark as successful
            processedEvent.markSuccess();
            processedEventRepository.save(processedEvent);

            log.info("Successfully processed Stripe webhook event: {} (ID: {})", event.getType(), eventId);
            return ResponseEntity.ok("Webhook processed successfully");

        } catch (IllegalStateException e) {
            // Duplicate detection from processor - acknowledge to stop retries
            log.info("Duplicate event from processor: {}", e.getMessage());
            processedEvent.markSkipped();
            processedEventRepository.save(processedEvent);
            return ResponseEntity.ok("Event acknowledged");

        } catch (IllegalArgumentException e) {
            // Bad data - acknowledge to stop retries (permanent error)
            log.warn("Invalid event data: {}", e.getMessage());
            processedEvent.markFailed(e.getMessage());
            processedEventRepository.save(processedEvent);
            return ResponseEntity.ok("Invalid data acknowledged");

        } catch (Exception e) {
            // Transient error - return 500 so Stripe retries
            log.error("Error processing Stripe webhook: {}", e.getMessage(), e);
            processedEvent.markFailed(e.getMessage());
            processedEventRepository.save(processedEvent);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Webhook processing failed: " + e.getMessage());
        }
    }

    private Event verifyWebhookSignature(String payload, String sigHeader) {
        try {
            return Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (Exception e) {
            log.error("Invalid webhook signature. Signature: {}", sigHeader, e);
            throw new IllegalArgumentException("Invalid webhook signature");
        }
    }

    private StripeWebhookEvent convertStripeEvent(Event event) {
        StripeWebhookEvent webhookEvent = new StripeWebhookEvent();
        webhookEvent.setEventId(event.getId());
        webhookEvent.setType(event.getType());

        try {
            switch (event.getType()) {
                case "customer.subscription.created":
                case "customer.subscription.updated":
                case "customer.subscription.deleted":
                case "customer.subscription.trial_will_end":
                    handleSubscriptionEvent(event, webhookEvent);
                    break;

                case "invoice.payment_succeeded":
                case "invoice.payment_failed":
                case "invoice.created":
                case "invoice.finalized":
                    handleInvoiceEvent(event, webhookEvent);
                    break;

                case "payment_intent.succeeded":
                case "payment_intent.payment_failed":
                    handlePaymentIntentEvent(event, webhookEvent);
                    break;

                case "charge.succeeded":
                case "charge.failed":
                case "charge.refunded":
                    handleChargeEvent(event, webhookEvent);
                    break;

                case "charge.dispute.created":
                case "charge.dispute.closed":
                    handleDisputeEvent(event, webhookEvent);
                    break;

                default:
                    log.info("Unhandled event type: {}", event.getType());
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
            if (price.getRecurring() != null) {
                webhookEvent.setInterval(price.getRecurring().getInterval());
                webhookEvent.setIntervalCount(price.getRecurring().getIntervalCount());
            }
        }

        // Important: Track cancelAtPeriodEnd separately from status
        webhookEvent.setCancelAtPeriodEnd(subscription.getCancelAtPeriodEnd());

        // Track trial period
        if (subscription.getTrialEnd() != null) {
            webhookEvent.setTrialEnd(convertToDateTime(subscription.getTrialEnd()));
            // Only mark as trial if trial hasn't ended yet
            long now = System.currentTimeMillis() / 1000;
            webhookEvent.setIsTrialPeriod(subscription.getTrialEnd() > now);
        } else {
            webhookEvent.setIsTrialPeriod(false);
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

        // For subscription invoices, get the period end
        if (invoice.getSubscription() != null && invoice.getLines() != null
                && !invoice.getLines().getData().isEmpty()) {
            InvoiceLineItem lineItem = invoice.getLines().getData().get(0);
            if (lineItem.getPeriod() != null && lineItem.getPeriod().getEnd() != null) {
                webhookEvent.setExpiresAt(convertToDateTime(lineItem.getPeriod().getEnd()));
            }
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

        // For refunds, track the refund amount
        if ("charge.refunded".equals(event.getType()) && charge.getAmountRefunded() != null) {
            webhookEvent.setAmount(charge.getAmountRefunded());
        }
    }

    private void handleDisputeEvent(Event event, StripeWebhookEvent webhookEvent) {
        Dispute dispute = (Dispute) event.getDataObjectDeserializer().getObject().get();

        webhookEvent.setAmount(dispute.getAmount());
        webhookEvent.setCurrency(dispute.getCurrency());
        webhookEvent.setStatus(dispute.getStatus());

        // Get the charge ID from the dispute
        if (dispute.getCharge() != null) {
            webhookEvent.setPaymentIntentId(dispute.getCharge());
        }
    }

    private void handleGenericEvent(Event event, StripeWebhookEvent webhookEvent) {
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
