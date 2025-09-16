package com.orbvpn.api.controller;

import com.orbvpn.api.domain.entity.CoinPaymentCallback;
import com.orbvpn.api.service.payment.PaymentService;
import com.orbvpn.api.service.payment.coinpayment.CoinPaymentService;
import com.orbvpn.api.service.webhook.WebhookEventCreator;
import com.orbvpn.api.service.webhook.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/webhook/coinpayment")
@RequiredArgsConstructor
@Slf4j
public class CoinPaymentWebhookController {

    private final CoinPaymentService coinPaymentService;
    private final PaymentService paymentService;
    private final WebhookService webhookService;
    private final WebhookEventCreator webhookEventCreator;

    @PostMapping("/ipn/{paymentId}")
    public ResponseEntity<String> handleIpnNotification(
            @PathVariable Long paymentId,
            @RequestBody Map<String, String> payload,
            @RequestHeader("HMAC") String hmac) {

        try {
            CoinPaymentCallback payment = coinPaymentService.getCallbackPayment(paymentId);
            if (payment == null) {
                return ResponseEntity.badRequest().body("Payment not found");
            }

            // Verify HMAC signature
            if (!verifyHmacSignature(payload, hmac, coinPaymentService.getIpnSecret())) {
                log.error("Invalid HMAC signature for payment {}", paymentId);
                return ResponseEntity.badRequest().body("Invalid signature");
            }

            int status = Integer.parseInt(payload.get("status"));
            double amount = Double.parseDouble(payload.get("amount"));

            // Update payment status
            payment.setStatus(status);
            coinPaymentService.save(payment);

            // Process successful payment
            if (status >= 100 || status == 2) {
                // Fulfill the payment
                paymentService.fullFillPayment(payment.getPayment());

                // Send webhook notification
                Map<String, Object> webhookPayload = new HashMap<>();
                webhookPayload.put("paymentId", payment.getPayment().getId());
                webhookPayload.put("amount", amount);
                webhookPayload.put("status", "completed");
                webhookPayload.put("gateway", "COINPAYMENT");
                webhookService.processWebhook("PAYMENT_COMPLETED", webhookPayload);
            }

            return ResponseEntity.ok("IPN processed");
        } catch (Exception e) {
            log.error("Error processing CoinPayment IPN", e);
            return ResponseEntity.internalServerError().body("Error processing IPN");
        }
    }

    private boolean verifyHmacSignature(Map<String, String> payload, String receivedHmac, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(), "HmacSHA512");
            mac.init(secretKeySpec);

            // Create payload string
            StringBuilder sb = new StringBuilder();
            payload.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> sb.append(entry.getKey()).append("=").append(entry.getValue()).append("&"));

            // Remove last &
            String payloadString = sb.substring(0, sb.length() - 1);

            byte[] hash = mac.doFinal(payloadString.getBytes());

            // Convert to hex
            Formatter formatter = new Formatter();
            for (byte b : hash) {
                formatter.format("%02x", b);
            }
            String calculatedHmac = formatter.toString();
            formatter.close();

            return calculatedHmac.equals(receivedHmac);
        } catch (Exception e) {
            log.error("Error verifying HMAC signature", e);
            return false;
        }
    }
}