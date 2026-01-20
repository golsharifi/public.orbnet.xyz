package com.orbvpn.api.controller;

import com.orbvpn.api.domain.entity.CoinPaymentCallback;
import com.orbvpn.api.domain.entity.ProcessedCoinPaymentWebhook;
import com.orbvpn.api.repository.ProcessedCoinPaymentWebhookRepository;
import com.orbvpn.api.service.payment.PaymentService;
import com.orbvpn.api.service.payment.coinpayment.CoinPaymentBaseService;
import com.orbvpn.api.service.payment.coinpayment.CoinPaymentService;
import com.orbvpn.api.service.payment.coinpayment.Constants;
import com.orbvpn.api.service.webhook.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * CoinPayment IPN (Instant Payment Notification) webhook controller.
 * Handles callbacks from CoinPayments when payment status changes.
 */
@RestController
@RequestMapping("/api/webhook/coinpayment")
@RequiredArgsConstructor
@Slf4j
public class CoinPaymentWebhookController {

    private final CoinPaymentService coinPaymentService;
    private final PaymentService paymentService;
    private final WebhookService webhookService;
    private final ProcessedCoinPaymentWebhookRepository webhookRepository;

    @PostMapping("/ipn/{paymentId}")
    public ResponseEntity<String> handleIpnNotification(
            @PathVariable Long paymentId,
            @RequestBody Map<String, String> payload,
            @RequestHeader(value = "HMAC", required = false) String hmacHeader,
            @RequestHeader(value = "hmac", required = false) String hmacHeaderLower) {

        log.info("Received CoinPayment IPN for payment ID: {}", paymentId);

        try {
            // Get HMAC from either header case
            String hmac = hmacHeader != null ? hmacHeader : hmacHeaderLower;
            if (hmac == null) {
                log.error("HMAC header missing for payment {}", paymentId);
                return ResponseEntity.badRequest().body("HMAC header required");
            }

            // Find the payment
            CoinPaymentCallback payment = coinPaymentService.getCallbackPayment(paymentId);
            if (payment == null) {
                log.error("Payment not found: {}", paymentId);
                return ResponseEntity.badRequest().body("Payment not found");
            }

            // Build payload string for verification (sorted by key)
            StringBuilder sb = new StringBuilder();
            payload.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> sb.append(entry.getKey())
                            .append("=")
                            .append(entry.getValue())
                            .append("&"));

            String payloadString = sb.length() > 0 ? sb.substring(0, sb.length() - 1) : "";
            payloadString = payloadString.replaceAll("@", "%40").replace(' ', '+');

            // Verify HMAC signature using unified implementation
            String calculatedHmac = CoinPaymentBaseService.buildHmacSignature(payloadString, coinPaymentService.getIpnSecret());
            if (!calculatedHmac.equalsIgnoreCase(hmac)) {
                log.error("Invalid HMAC signature for payment {}", paymentId);
                return ResponseEntity.badRequest().body("Invalid signature");
            }

            // Parse status and amount (using BigDecimal for precision)
            int status = parseIntSafe(payload.get("status"));
            BigDecimal amount = parseBigDecimalSafe(payload.get("amount"));
            String txnId = payload.get("txn_id");

            log.info("IPN - Payment ID: {}, Status: {}, Amount: {}, TxnId: {}", paymentId, status, amount, txnId);

            // Generate unique IPN ID for idempotency
            String ipnId = ProcessedCoinPaymentWebhook.generateIpnId(paymentId, txnId, status);

            // Check if already processed (idempotency)
            if (webhookRepository.existsByIpnId(ipnId)) {
                log.info("IPN already processed: {}", ipnId);
                return ResponseEntity.ok("IPN already processed");
            }

            // Check associated payment exists
            if (payment.getPayment() == null) {
                log.error("Associated payment not found for CoinPayment ID: {}", paymentId);
                return ResponseEntity.badRequest().body("Associated payment not found");
            }

            // Record the webhook processing attempt
            ProcessedCoinPaymentWebhook processedWebhook = ProcessedCoinPaymentWebhook.builder()
                    .ipnId(ipnId)
                    .paymentId(paymentId)
                    .txnId(txnId)
                    .status(status)
                    .amount(amount)
                    .build();

            // Update payment status
            payment.setStatus(status);
            coinPaymentService.save(payment);

            // Process based on status using constants
            if (Constants.isPaymentComplete(status)) {
                // Verify amount is sufficient (using BigDecimal comparison)
                if (payment.getCoinAmount().compareTo(amount) > 0) {
                    log.warn("Insufficient payment amount. Expected: {}, Received: {}",
                            payment.getCoinAmount(), amount);
                    processedWebhook.setSuccessful(false);
                    processedWebhook.setErrorMessage("Insufficient amount");
                    webhookRepository.save(processedWebhook);
                    return ResponseEntity.badRequest().body("Insufficient payment amount");
                }

                // Fulfill the payment
                paymentService.fullFillPayment(payment.getPayment());

                // Send webhook notification to external systems
                Map<String, Object> webhookPayload = new HashMap<>();
                webhookPayload.put("paymentId", payment.getPayment().getId());
                webhookPayload.put("amount", amount);
                webhookPayload.put("status", "completed");
                webhookPayload.put("gateway", "COINPAYMENT");
                webhookService.processWebhook("PAYMENT_COMPLETED", webhookPayload);

                processedWebhook.setSuccessful(true);
                log.info("Payment fulfilled successfully for ID: {}", paymentId);

            } else if (Constants.isPaymentFailed(status)) {
                log.warn("Payment failed with status: {}", status);
                processedWebhook.setSuccessful(true);
                processedWebhook.setErrorMessage("Payment failed with status: " + status);

            } else {
                // Pending status
                log.info("Payment pending with status: {}", status);
                processedWebhook.setSuccessful(true);
            }

            webhookRepository.save(processedWebhook);
            return ResponseEntity.ok("IPN processed");

        } catch (Exception e) {
            log.error("Error processing CoinPayment IPN for payment {}", paymentId, e);
            return ResponseEntity.internalServerError().body("Error processing IPN");
        }
    }

    private int parseIntSafe(String value) {
        try {
            return value != null ? Integer.parseInt(value) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private BigDecimal parseBigDecimalSafe(String value) {
        try {
            return value != null ? new BigDecimal(value) : BigDecimal.ZERO;
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}