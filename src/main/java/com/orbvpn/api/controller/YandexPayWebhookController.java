package com.orbvpn.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbvpn.api.domain.entity.Payment;
import com.orbvpn.api.domain.entity.ProcessedYandexPayWebhook;
import com.orbvpn.api.domain.entity.YandexPayment;
import com.orbvpn.api.domain.enums.PaymentStatus;
import com.orbvpn.api.domain.payload.YandexPay.WebhookPayload;
import com.orbvpn.api.repository.ProcessedYandexPayWebhookRepository;
import com.orbvpn.api.service.InvoiceService;
import com.orbvpn.api.service.payment.PaymentService;
import com.orbvpn.api.service.payment.yandexpay.YandexPayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * Webhook controller for Yandex Pay.
 * Receives callbacks when payment status changes.
 *
 * Webhook events:
 * - ORDER_CREATED: Order was created
 * - ORDER_PAID: Payment authorized
 * - ORDER_CAPTURED: Payment captured successfully
 * - ORDER_CANCELLED: Order cancelled
 * - ORDER_REFUNDED: Payment refunded
 * - ORDER_FAILED: Payment failed
 */
@Slf4j
@RestController
@RequestMapping("/api/webhooks/yandex-pay")
@RequiredArgsConstructor
public class YandexPayWebhookController {

    private final YandexPayService yandexPayService;
    private final PaymentService paymentService;
    private final InvoiceService invoiceService;
    private final ObjectMapper objectMapper;
    private final ProcessedYandexPayWebhookRepository processedWebhookRepository;

    /**
     * Handle webhook callbacks from Yandex Pay.
     * Yandex Pay sends JWT tokens in the request body.
     */
    @PostMapping("/v1/webhook")
    @Transactional
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Content-Type", required = false) String contentType) {

        log.info("Received Yandex Pay webhook");

        try {
            // Parse the webhook payload
            WebhookPayload webhookPayload;

            // Check if payload is JWT or JSON
            if (payload.startsWith("{")) {
                // Direct JSON payload
                webhookPayload = objectMapper.readValue(payload, WebhookPayload.class);
            } else {
                // JWT payload - verify and parse
                if (!yandexPayService.verifyWebhookSignature(payload)) {
                    log.error("Invalid Yandex Pay webhook signature");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
                }

                webhookPayload = yandexPayService.parseWebhookPayload(payload);
                if (webhookPayload == null) {
                    log.error("Failed to parse Yandex Pay webhook payload");
                    return ResponseEntity.badRequest().body("Invalid payload");
                }
            }

            String event = webhookPayload.getEvent();
            String orderId = webhookPayload.getOrderId();
            String status = webhookPayload.getStatus();

            log.info("Yandex Pay webhook - Event: {}, OrderId: {}, Status: {}",
                    event, orderId, status);

            // IDEMPOTENCY CHECK: Check if this exact order + event + status was already processed
            if (processedWebhookRepository.existsByOrderIdAndEventAndPaymentStatus(orderId, event, status)) {
                log.info("Duplicate Yandex Pay webhook detected - OrderId: {}, Event: {}, Status: {} - skipping",
                        orderId, event, status);
                recordWebhook(webhookPayload, payload, null,
                        ProcessedYandexPayWebhook.STATUS_DUPLICATE, null);
                return ResponseEntity.ok("Duplicate webhook - already processed");
            }

            // Find the payment
            Optional<YandexPayment> paymentOpt = yandexPayService.findByOrderId(orderId);

            // Try by Yandex order ID if not found
            if (paymentOpt.isEmpty() && orderId != null) {
                paymentOpt = yandexPayService.findByYandexOrderId(orderId);
            }

            if (paymentOpt.isEmpty()) {
                log.warn("Payment not found for Yandex Pay webhook - OrderId: {}", orderId);
                recordWebhook(webhookPayload, payload, null,
                        ProcessedYandexPayWebhook.STATUS_SKIPPED, "Payment not found");
                // Return 200 to stop retries
                return ResponseEntity.ok("Payment not found - acknowledged");
            }

            YandexPayment yandexPayment = paymentOpt.get();
            Integer internalPaymentId = yandexPayment.getPayment() != null ?
                    yandexPayment.getPayment().getId() : null;

            try {
                // Store raw payload for debugging
                yandexPayment.setRawWebhookPayload(payload);

                // Update operation ID if present
                if (webhookPayload.getOperationId() != null) {
                    yandexPayment.setOperationId(webhookPayload.getOperationId());
                }

                // Update payment method if present
                if (webhookPayload.getPaymentMethod() != null) {
                    yandexPayment.setPaymentMethod(webhookPayload.getPaymentMethod());
                }

                // Process based on event/status
                if (webhookPayload.isSuccessfulPayment()) {
                    handleSuccessfulPayment(yandexPayment, webhookPayload);
                } else if (webhookPayload.isFailedPayment()) {
                    handleFailedPayment(yandexPayment, webhookPayload);
                } else if (webhookPayload.isRefundEvent()) {
                    handleRefundedPayment(yandexPayment, webhookPayload);
                } else {
                    // Update status based on webhook
                    handleStatusUpdate(yandexPayment, webhookPayload);
                }

                yandexPayService.save(yandexPayment);

                // Record successful processing
                recordWebhook(webhookPayload, payload, internalPaymentId,
                        ProcessedYandexPayWebhook.STATUS_SUCCESS, null);

                return ResponseEntity.ok("Webhook processed");

            } catch (Exception e) {
                log.error("Error processing Yandex Pay webhook for OrderId: {}", orderId, e);
                recordWebhook(webhookPayload, payload, internalPaymentId,
                        ProcessedYandexPayWebhook.STATUS_FAILED, e.getMessage());
                throw e;
            }

        } catch (Exception e) {
            log.error("Error processing Yandex Pay webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing webhook");
        }
    }

    /**
     * Handle successful payment
     */
    private void handleSuccessfulPayment(YandexPayment yandexPayment, WebhookPayload payload) {
        log.info("Processing successful Yandex Pay payment: {}", yandexPayment.getOrderId());

        Payment payment = yandexPayment.getPayment();

        // Check if already processed (idempotency)
        if (yandexPayment.isSuccessful()) {
            log.info("Payment {} already succeeded, skipping fulfillment", yandexPayment.getOrderId());
            return;
        }

        // Update Yandex payment status
        yandexPayment.setPaymentStatus(YandexPayment.STATUS_CONFIRMED);

        // Fulfill the payment if we have a reference
        if (payment != null) {
            if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
                payment.setStatus(PaymentStatus.SUCCEEDED);
                paymentService.fullFillPayment(payment);
                log.info("Yandex Pay payment {} fulfilled successfully", yandexPayment.getOrderId());
            }
        }
    }

    /**
     * Handle failed payment
     */
    private void handleFailedPayment(YandexPayment yandexPayment, WebhookPayload payload) {
        log.warn("Processing failed Yandex Pay payment: {}", yandexPayment.getOrderId());

        String errorMessage = payload.getReason() != null ?
                payload.getReason() : "Payment failed";

        yandexPayment.markFailed(errorMessage);

        Payment payment = yandexPayment.getPayment();
        if (payment != null) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setErrorMessage(errorMessage);
        }
    }

    /**
     * Handle refunded payment
     */
    private void handleRefundedPayment(YandexPayment yandexPayment, WebhookPayload payload) {
        log.info("Processing refunded Yandex Pay payment: {}", yandexPayment.getOrderId());

        yandexPayment.markRefunded();

        Payment payment = yandexPayment.getPayment();
        if (payment != null) {
            payment.setStatus(PaymentStatus.REFUNDED);
            // Sync invoice status
            invoiceService.updateInvoiceStatusFromPayment(payment);
        }
    }

    /**
     * Handle status update for other events
     */
    private void handleStatusUpdate(YandexPayment yandexPayment, WebhookPayload payload) {
        String status = payload.getStatus();

        if (status == null) {
            status = payload.getEvent();
        }

        log.info("Updating Yandex Pay payment {} status to: {}",
                yandexPayment.getOrderId(), status);

        // Map webhook status to internal status
        switch (status.toUpperCase()) {
            case "ORDER_CREATED", "PENDING" -> yandexPayment.setPaymentStatus(YandexPayment.STATUS_PENDING);
            case "AUTHORIZED" -> yandexPayment.setPaymentStatus(YandexPayment.STATUS_AUTHORIZED);
            case "CAPTURED" -> yandexPayment.setPaymentStatus(YandexPayment.STATUS_CAPTURED);
            case "ORDER_CANCELLED", "CANCELLED" -> yandexPayment.markCancelled();
            default -> {
                log.info("Unhandled status: {}", status);
                yandexPayment.setPaymentStatus(status);
            }
        }

        // Update Payment entity status if applicable
        Payment payment = yandexPayment.getPayment();
        if (payment != null) {
            switch (status.toUpperCase()) {
                case "PENDING", "ORDER_CREATED" -> payment.setStatus(PaymentStatus.PENDING);
                case "AUTHORIZED" -> payment.setStatus(PaymentStatus.PROCESSING);
                case "ORDER_CANCELLED", "CANCELLED" -> payment.setStatus(PaymentStatus.FAILED);
            }
        }
    }

    private void recordWebhook(WebhookPayload payload, String rawPayload,
                               Integer internalPaymentId, String status, String errorMessage) {
        try {
            ProcessedYandexPayWebhook record = ProcessedYandexPayWebhook.builder()
                    .orderId(payload.getOrderId())
                    .yandexOrderId(payload.getOrderId())
                    .event(payload.getEvent())
                    .paymentStatus(payload.getStatus())
                    .operationId(payload.getOperationId())
                    .amount(payload.getAmount())
                    .currency(payload.getCurrency())
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

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
