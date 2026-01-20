package com.orbvpn.api.controller;

import java.math.BigDecimal;
import java.util.Enumeration;

import jakarta.servlet.http.HttpServletRequest;

import com.orbvpn.api.domain.entity.ProcessedCoinPaymentWebhook;
import com.orbvpn.api.repository.ProcessedCoinPaymentWebhookRepository;
import com.orbvpn.api.service.payment.PaymentService;
import com.orbvpn.api.service.payment.coinpayment.CoinPaymentBaseService;
import com.orbvpn.api.service.payment.coinpayment.CoinPaymentService;
import com.orbvpn.api.service.payment.coinpayment.Constants;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

/**
 * Legacy CoinPayment IPN webhook controller.
 * @deprecated Use {@link CoinPaymentWebhookController} at /api/webhook/coinpayment/ipn instead.
 * This endpoint is maintained for backward compatibility with existing CoinPayment configurations.
 */
@Slf4j
@RestController
@RequestMapping("/")
@Deprecated
public class CoinPaymentController {

    @Value("${coinpayment.ipn.secret}")
    protected String coinPaymentIpnSecret;

    @Value("${coinpayment.merchant-id}")
    protected String merchantId;

    private final CoinPaymentService coinPaymentService;
    private final PaymentService paymentService;
    private final ProcessedCoinPaymentWebhookRepository webhookRepository;

    public CoinPaymentController(CoinPaymentService coinPaymentService,
            PaymentService paymentService,
            ProcessedCoinPaymentWebhookRepository webhookRepository) {
        this.coinPaymentService = coinPaymentService;
        this.paymentService = paymentService;
        this.webhookRepository = webhookRepository;
    }

    @PostMapping("/ipn/{id}")
    @ResponseBody
    public ResponseEntity<String> handleIpn(@PathVariable("id") Long id, HttpServletRequest request) {
        log.info("Received CoinPayment IPN for payment ID: {}", id);

        try {
            // Validate IPN mode - with null check
            String ipnMode = request.getParameter("ipn_mode");
            if (ipnMode == null || !ipnMode.equals("hmac")) {
                log.error("IPN Mode is not HMAC: {}", ipnMode);
                return ResponseEntity.badRequest().body("IPN Mode is not HMAC");
            }

            // Get HMAC header - case insensitive lookup
            String hmac = getHmacHeader(request);
            if (hmac == null) {
                log.error("IPN HMAC header is missing");
                return ResponseEntity.badRequest().body("IPN HMAC is missing");
            }

            // Validate merchant ID
            String merchant = request.getParameter("merchant");
            if (merchant == null || !merchant.equals(merchantId)) {
                log.error("No or incorrect Merchant ID passed: {}", merchant);
                return ResponseEntity.badRequest().body("Invalid Merchant ID");
            }

            // Build query string for signature verification
            StringBuilder queryBuilder = new StringBuilder();
            Enumeration<String> paramNames = request.getParameterNames();
            while (paramNames.hasMoreElements()) {
                String paramName = paramNames.nextElement();
                queryBuilder.append(paramName)
                        .append("=")
                        .append(request.getParameter(paramName))
                        .append("&");
            }

            String reqQuery = queryBuilder.substring(0, queryBuilder.length() - 1);
            reqQuery = reqQuery.replaceAll("@", "%40").replace(' ', '+');
            log.debug("IPN reqQuery: {}", reqQuery);

            // Verify HMAC signature using the shared implementation
            String calculatedHmac = CoinPaymentBaseService.buildHmacSignature(reqQuery, coinPaymentIpnSecret);
            if (!calculatedHmac.equalsIgnoreCase(hmac)) {
                log.error("HMAC signature mismatch");
                return ResponseEntity.badRequest().body("Invalid signature");
            }

            // Extract payment details (using BigDecimal for precision)
            BigDecimal amount = getBigDecimal(request, "amount");
            int status = getInt(request, "status");
            String txnId = request.getParameter("txn_id");

            log.info("IPN - Payment ID: {}, Status: {}, Amount: {}, TxnId: {}", id, status, amount, txnId);

            // Generate unique IPN ID for idempotency
            String ipnId = ProcessedCoinPaymentWebhook.generateIpnId(id, txnId, status);

            // Check if already processed (idempotency)
            if (webhookRepository.existsByIpnId(ipnId)) {
                log.info("IPN already processed: {}", ipnId);
                return ResponseEntity.ok("IPN already processed");
            }

            // Get the payment record
            var cryptoPayment = coinPaymentService.getCallbackPayment(id);
            if (cryptoPayment == null) {
                log.error("Payment not found for ID: {}", id);
                return ResponseEntity.badRequest().body("Payment not found");
            }

            var payment = cryptoPayment.getPayment();
            if (payment == null) {
                log.error("Associated payment not found for CoinPayment ID: {}", id);
                return ResponseEntity.badRequest().body("Associated payment not found");
            }

            // Record the webhook processing attempt
            ProcessedCoinPaymentWebhook processedWebhook = ProcessedCoinPaymentWebhook.builder()
                    .ipnId(ipnId)
                    .paymentId(id)
                    .txnId(txnId)
                    .status(status)
                    .amount(amount)
                    .build();

            // Process based on status using constants
            if (Constants.isPaymentComplete(status)) {
                // Verify amount is sufficient (using BigDecimal comparison)
                if (cryptoPayment.getCoinAmount().compareTo(amount) > 0) {
                    log.warn("Insufficient payment amount. Expected: {}, Received: {}",
                            cryptoPayment.getCoinAmount(), amount);
                    processedWebhook.setSuccessful(false);
                    processedWebhook.setErrorMessage("Insufficient amount");
                    webhookRepository.save(processedWebhook);
                    return ResponseEntity.badRequest().body("Insufficient payment amount");
                }

                // Mark as paid
                cryptoPayment.setStatus(Constants.STATUS_CONFIRMING);
                coinPaymentService.save(cryptoPayment);

                // Fulfill payment
                paymentService.fullFillPayment(payment);

                processedWebhook.setSuccessful(true);
                log.info("Payment fulfilled successfully for ID: {}", id);

            } else if (Constants.isPaymentFailed(status)) {
                log.warn("Payment failed with status: {}", status);
                processedWebhook.setSuccessful(true); // Successfully processed (even though payment failed)
                processedWebhook.setErrorMessage("Payment failed with status: " + status);

            } else {
                // Pending status - just acknowledge
                log.info("Payment pending with status: {}", status);
                processedWebhook.setSuccessful(true);
            }

            webhookRepository.save(processedWebhook);
            return ResponseEntity.ok("IPN processed");

        } catch (Exception e) {
            log.error("Error processing CoinPayment IPN for ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing IPN");
        }
    }

    /**
     * Get HMAC header with case-insensitive lookup.
     * CoinPayments may send 'HMAC' or 'hmac' depending on configuration.
     */
    private String getHmacHeader(HttpServletRequest request) {
        String hmac = request.getHeader("HMAC");
        if (hmac == null) {
            hmac = request.getHeader("hmac");
        }
        if (hmac == null) {
            hmac = request.getHeader("Hmac");
        }
        return hmac;
    }

    private int getInt(HttpServletRequest request, String param) {
        try {
            String value = request.getParameter(param);
            if (value == null) {
                return 0;
            }
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private BigDecimal getBigDecimal(HttpServletRequest request, String param) {
        try {
            String value = request.getParameter(param);
            if (value == null) {
                return BigDecimal.ZERO;
            }
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
