package com.orbvpn.api.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbvpn.api.domain.entity.NowPayment;
import com.orbvpn.api.domain.entity.ProcessedNowPaymentWebhook;
import com.orbvpn.api.domain.enums.PaymentStatus;
import com.orbvpn.api.domain.payload.NowPayment.IpnCallbackPayload;
import com.orbvpn.api.repository.ProcessedNowPaymentWebhookRepository;
import com.orbvpn.api.service.InvoiceService;
import com.orbvpn.api.service.payment.PaymentService;
import com.orbvpn.api.service.payment.nowpayment.NowPaymentService;
import com.orbvpn.api.service.webhook.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Webhook controller for NOWPayments IPN (Instant Payment Notifications).
 * Receives callbacks when payment status changes.
 */
@Slf4j
@RestController
@RequestMapping("/api/webhooks/nowpayment")
@RequiredArgsConstructor
public class NowPaymentWebhookController {

    private final NowPaymentService nowPaymentService;
    private final PaymentService paymentService;
    private final InvoiceService invoiceService;
    private final WebhookService webhookService;
    private final ObjectMapper objectMapper;
    private final ProcessedNowPaymentWebhookRepository processedWebhookRepository;

    /**
     * Handle IPN callbacks from NOWPayments.
     * The signature is verified using HMAC-SHA512 with the IPN secret.
     */
    @PostMapping("/ipn")
    @Transactional
    public ResponseEntity<String> handleIpnCallback(
            @RequestBody String payload,
            @RequestHeader(value = "x-nowpayments-sig", required = false) String signature) {

        log.info("Received NOWPayments IPN callback");

        try {
            // Verify signature if provided
            if (signature != null && !signature.isEmpty()) {
                if (!nowPaymentService.verifyIpnSignature(payload, signature)) {
                    log.error("Invalid NOWPayments IPN signature");
                    return ResponseEntity.badRequest().body("Invalid signature");
                }
            } else {
                log.warn("NOWPayments IPN callback received without signature");
            }

            // Parse the callback payload
            IpnCallbackPayload callback;
            try {
                callback = objectMapper.readValue(payload, IpnCallbackPayload.class);
            } catch (JsonProcessingException e) {
                log.error("Failed to parse NOWPayments IPN payload", e);
                return ResponseEntity.badRequest().body("Invalid payload format");
            }

            String paymentId = String.valueOf(callback.getPaymentId());
            String paymentStatus = callback.getPaymentStatus();

            log.info("Processing NOWPayments IPN - OrderId: {}, PaymentId: {}, Status: {}",
                    callback.getOrderId(), paymentId, paymentStatus);

            // IDEMPOTENCY CHECK: Check if this exact payment+status combination was already processed
            if (processedWebhookRepository.existsByPaymentIdAndPaymentStatus(paymentId, paymentStatus)) {
                log.info("Duplicate IPN callback detected for PaymentId: {}, Status: {} - skipping",
                        paymentId, paymentStatus);
                // Record as duplicate for auditing
                recordWebhook(callback, payload, null, ProcessedNowPaymentWebhook.STATUS_DUPLICATE, null);
                return ResponseEntity.ok("Duplicate IPN - already processed");
            }

            // Find the payment by order ID
            Optional<NowPayment> paymentOpt = nowPaymentService.findByOrderId(callback.getOrderId());
            if (paymentOpt.isEmpty()) {
                // Try by payment ID
                paymentOpt = nowPaymentService.findByPaymentId(paymentId);
            }

            if (paymentOpt.isEmpty()) {
                log.warn("No payment found for NOWPayments callback - OrderId: {}, PaymentId: {}",
                        callback.getOrderId(), paymentId);
                // Record as skipped
                recordWebhook(callback, payload, null, ProcessedNowPaymentWebhook.STATUS_SKIPPED,
                        "Payment not found");
                // Return 200 to stop retries - we don't have this payment
                return ResponseEntity.ok("Payment not found - acknowledged");
            }

            NowPayment nowPayment = paymentOpt.get();
            Integer internalPaymentId = nowPayment.getPayment() != null ?
                    nowPayment.getPayment().getId() : null;

            try {
                // Update payment status
                nowPayment.setPaymentStatus(paymentStatus);
                nowPayment.setActuallyPaid(callback.getActuallyPaid());
                if (callback.getOutcomeAmount() != null) {
                    nowPayment.setOutcomeAmount(callback.getOutcomeAmount());
                    nowPayment.setOutcomeCurrency(callback.getOutcomeCurrency());
                }
                nowPaymentService.save(nowPayment);

                // Process based on status
                processPaymentStatus(nowPayment, callback);

                // Record successful processing
                recordWebhook(callback, payload, internalPaymentId,
                        ProcessedNowPaymentWebhook.STATUS_SUCCESS, null);

                return ResponseEntity.ok("IPN processed successfully");

            } catch (Exception e) {
                log.error("Error processing payment status for PaymentId: {}", paymentId, e);
                // Record failed processing
                recordWebhook(callback, payload, internalPaymentId,
                        ProcessedNowPaymentWebhook.STATUS_FAILED, e.getMessage());
                throw e;
            }

        } catch (Exception e) {
            log.error("Error processing NOWPayments IPN callback", e);
            // Return 500 so NOWPayments will retry
            return ResponseEntity.internalServerError().body("Error processing IPN");
        }
    }

    private void processPaymentStatus(NowPayment nowPayment, IpnCallbackPayload callback) {
        switch (callback.getPaymentStatus().toLowerCase()) {
            case "finished", "confirmed" -> {
                // Payment successful - fulfill the order
                log.info("Payment {} successful - fulfilling order", nowPayment.getId());

                if (nowPayment.getPayment() != null &&
                    nowPayment.getPayment().getStatus() != PaymentStatus.SUCCEEDED) {
                    paymentService.fullFillPayment(nowPayment.getPayment());
                }

                // Send webhook notification
                sendWebhookNotification("PAYMENT_COMPLETED", nowPayment, callback);
            }

            case "partially_paid" -> {
                // Partial payment received
                log.warn("Payment {} partially paid - expected: {}, received: {}",
                        nowPayment.getId(), nowPayment.getPayAmount(), callback.getActuallyPaid());

                if (nowPayment.getPayment() != null) {
                    nowPayment.getPayment().setStatus(PaymentStatus.PENDING);
                    nowPayment.getPayment().setErrorMessage(
                            "Partial payment received: " + callback.getActuallyPaid() + " " + callback.getPayCurrency());
                }

                sendWebhookNotification("PAYMENT_PARTIAL", nowPayment, callback);
            }

            case "confirming", "sending" -> {
                // Payment in progress
                log.info("Payment {} in progress - status: {}", nowPayment.getId(), callback.getPaymentStatus());
                sendWebhookNotification("PAYMENT_PROCESSING", nowPayment, callback);
            }

            case "failed" -> {
                // Payment failed
                log.warn("Payment {} failed", nowPayment.getId());

                if (nowPayment.getPayment() != null) {
                    nowPayment.getPayment().setStatus(PaymentStatus.FAILED);
                    nowPayment.getPayment().setErrorMessage("Cryptocurrency payment failed");
                }

                sendWebhookNotification("PAYMENT_FAILED", nowPayment, callback);
            }

            case "expired" -> {
                // Payment expired
                log.info("Payment {} expired", nowPayment.getId());

                if (nowPayment.getPayment() != null) {
                    nowPayment.getPayment().setStatus(PaymentStatus.EXPIRED);
                    nowPayment.getPayment().setErrorMessage("Cryptocurrency payment expired - no payment received");
                }

                sendWebhookNotification("PAYMENT_EXPIRED", nowPayment, callback);
            }

            case "refunded" -> {
                // Payment refunded
                log.info("Payment {} refunded", nowPayment.getId());

                if (nowPayment.getPayment() != null) {
                    nowPayment.getPayment().setStatus(PaymentStatus.REFUNDED);
                    // Sync invoice status
                    invoiceService.updateInvoiceStatusFromPayment(nowPayment.getPayment());
                }

                sendWebhookNotification("PAYMENT_REFUNDED", nowPayment, callback);
            }

            default -> {
                log.info("Payment {} - unhandled status: {}", nowPayment.getId(), callback.getPaymentStatus());
            }
        }
    }

    private void recordWebhook(IpnCallbackPayload callback, String rawPayload,
                               Integer internalPaymentId, String status, String errorMessage) {
        try {
            ProcessedNowPaymentWebhook record = ProcessedNowPaymentWebhook.builder()
                    .paymentId(String.valueOf(callback.getPaymentId()))
                    .orderId(callback.getOrderId())
                    .paymentStatus(callback.getPaymentStatus())
                    .actuallyPaid(callback.getActuallyPaid())
                    .payCurrency(callback.getPayCurrency())
                    .internalPaymentId(internalPaymentId)
                    .status(status)
                    .errorMessage(errorMessage)
                    .rawPayload(rawPayload)
                    .build();
            processedWebhookRepository.save(record);
        } catch (Exception e) {
            log.error("Failed to record webhook processing", e);
        }
    }

    private void sendWebhookNotification(String eventType, NowPayment nowPayment, IpnCallbackPayload callback) {
        try {
            Map<String, Object> webhookPayload = new HashMap<>();
            webhookPayload.put("paymentId", nowPayment.getPayment() != null ? nowPayment.getPayment().getId() : null);
            webhookPayload.put("nowPaymentId", nowPayment.getPaymentId());
            webhookPayload.put("orderId", nowPayment.getOrderId());
            webhookPayload.put("amount", callback.getActuallyPaid());
            webhookPayload.put("currency", callback.getPayCurrency());
            webhookPayload.put("status", callback.getPaymentStatus());
            webhookPayload.put("gateway", "NOWPAYMENTS");

            webhookService.processWebhook(eventType, webhookPayload);
        } catch (Exception e) {
            log.error("Failed to send webhook notification", e);
        }
    }
}
