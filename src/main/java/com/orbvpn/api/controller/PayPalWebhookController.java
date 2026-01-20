package com.orbvpn.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbvpn.api.domain.entity.Payment;
import com.orbvpn.api.domain.entity.ProcessedPayPalWebhook;
import com.orbvpn.api.domain.enums.GatewayName;
import com.orbvpn.api.domain.enums.PaymentStatus;
import com.orbvpn.api.repository.PaymentRepository;
import com.orbvpn.api.repository.ProcessedPayPalWebhookRepository;
import com.orbvpn.api.service.InvoiceService;
import com.orbvpn.api.service.payment.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PayPal Webhook Controller
 * Handles PayPal IPN (Instant Payment Notification) events.
 *
 * Supported events:
 * - CHECKOUT.ORDER.APPROVED: Order approved by buyer
 * - PAYMENT.CAPTURE.COMPLETED: Payment captured successfully
 * - PAYMENT.CAPTURE.DENIED: Payment capture failed
 * - PAYMENT.CAPTURE.REFUNDED: Payment refunded
 */
@RestController
@RequestMapping("/api/webhooks/paypal")
@Slf4j
public class PayPalWebhookController {

    private final ProcessedPayPalWebhookRepository webhookRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final InvoiceService invoiceService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    // Certificate cache to avoid fetching certificates repeatedly
    private final Map<String, X509Certificate> certificateCache = new ConcurrentHashMap<>();

    @Value("${paypal.webhook-id:}")
    private String webhookId;

    @Value("${paypal.webhook.verify-signature:true}")
    private boolean verifySignature;

    public PayPalWebhookController(ProcessedPayPalWebhookRepository webhookRepository,
                                    PaymentRepository paymentRepository,
                                    PaymentService paymentService,
                                    InvoiceService invoiceService,
                                    ObjectMapper objectMapper) {
        this.webhookRepository = webhookRepository;
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
        this.invoiceService = invoiceService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // PayPal event types
    private static final String EVENT_CHECKOUT_ORDER_APPROVED = "CHECKOUT.ORDER.APPROVED";
    private static final String EVENT_PAYMENT_CAPTURE_COMPLETED = "PAYMENT.CAPTURE.COMPLETED";
    private static final String EVENT_PAYMENT_CAPTURE_DENIED = "PAYMENT.CAPTURE.DENIED";
    private static final String EVENT_PAYMENT_CAPTURE_REFUNDED = "PAYMENT.CAPTURE.REFUNDED";

    @PostMapping
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "PAYPAL-TRANSMISSION-ID", required = false) String transmissionId,
            @RequestHeader(value = "PAYPAL-TRANSMISSION-TIME", required = false) String transmissionTime,
            @RequestHeader(value = "PAYPAL-TRANSMISSION-SIG", required = false) String transmissionSig,
            @RequestHeader(value = "PAYPAL-CERT-URL", required = false) String certUrl,
            @RequestHeader(value = "PAYPAL-AUTH-ALGO", required = false) String authAlgo) {

        log.info("Received PayPal webhook");

        try {
            // Parse the webhook payload
            JsonNode webhookEvent = objectMapper.readTree(payload);
            String eventId = webhookEvent.path("id").asText();
            String eventType = webhookEvent.path("event_type").asText();

            log.info("PayPal webhook - Event ID: {}, Type: {}", eventId, eventType);

            // Check for duplicate (idempotency)
            if (webhookRepository.existsByEventId(eventId)) {
                log.info("PayPal webhook already processed: {}", eventId);
                return ResponseEntity.ok("Webhook already processed");
            }

            // Verify webhook signature (if enabled and headers present)
            if (verifySignature && webhookId != null && !webhookId.isEmpty()) {
                if (!verifyWebhookSignature(transmissionId, transmissionTime, webhookId,
                        transmissionSig, certUrl, authAlgo, payload)) {
                    log.error("PayPal webhook signature verification failed for event: {}", eventId);
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
                }
            }

            // Extract resource information
            JsonNode resource = webhookEvent.path("resource");
            String resourceId = extractResourceId(resource, eventType);
            String resourceType = webhookEvent.path("resource_type").asText();

            // Create processed webhook record
            ProcessedPayPalWebhook processedWebhook = ProcessedPayPalWebhook.builder()
                    .eventId(eventId)
                    .eventType(eventType)
                    .resourceId(resourceId)
                    .resourceType(resourceType)
                    .rawPayload(payload)
                    .build();

            // Process based on event type
            switch (eventType) {
                case EVENT_CHECKOUT_ORDER_APPROVED:
                    handleOrderApproved(resource, processedWebhook);
                    break;

                case EVENT_PAYMENT_CAPTURE_COMPLETED:
                    handleCaptureCompleted(resource, processedWebhook);
                    break;

                case EVENT_PAYMENT_CAPTURE_DENIED:
                    handleCaptureDenied(resource, processedWebhook);
                    break;

                case EVENT_PAYMENT_CAPTURE_REFUNDED:
                    handleCaptureRefunded(resource, processedWebhook);
                    break;

                default:
                    log.info("Unhandled PayPal event type: {}", eventType);
                    processedWebhook.markSkipped();
            }

            webhookRepository.save(processedWebhook);
            return ResponseEntity.ok("Webhook processed");

        } catch (Exception e) {
            log.error("Error processing PayPal webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing webhook");
        }
    }

    /**
     * Handle CHECKOUT.ORDER.APPROVED event
     * This occurs when a buyer approves the payment on PayPal's site
     */
    private void handleOrderApproved(JsonNode resource, ProcessedPayPalWebhook processedWebhook) {
        String orderId = resource.path("id").asText();
        log.info("Processing PayPal order approved: {}", orderId);

        Optional<Payment> paymentOpt = paymentRepository.findByGatewayAndPaymentId(
                GatewayName.PAYPAL, orderId);

        if (paymentOpt.isEmpty()) {
            log.warn("Payment not found for PayPal order: {}", orderId);
            processedWebhook.markFailed("Payment not found for order: " + orderId);
            return;
        }

        Payment payment = paymentOpt.get();
        processedWebhook.setPaymentId(payment.getId());

        // Update payment status to indicate approval (not yet captured)
        if (payment.getStatus() == PaymentStatus.PENDING) {
            payment.setStatus(PaymentStatus.PROCESSING);
            paymentRepository.save(payment);
            log.info("Payment {} marked as processing (order approved)", payment.getId());
        }

        processedWebhook.markSuccess();
    }

    /**
     * Handle PAYMENT.CAPTURE.COMPLETED event
     * This occurs when the payment has been successfully captured
     */
    private void handleCaptureCompleted(JsonNode resource, ProcessedPayPalWebhook processedWebhook) {
        // For captures, we need to find the order ID from supplementary_data or links
        String captureId = resource.path("id").asText();
        String orderId = extractOrderIdFromCapture(resource);

        log.info("Processing PayPal capture completed - Capture ID: {}, Order ID: {}", captureId, orderId);

        Optional<Payment> paymentOpt = Optional.empty();

        // Try to find by order ID first
        if (orderId != null && !orderId.isEmpty()) {
            paymentOpt = paymentRepository.findByGatewayAndPaymentId(GatewayName.PAYPAL, orderId);
        }

        // If not found, try by capture ID
        if (paymentOpt.isEmpty()) {
            paymentOpt = paymentRepository.findByGatewayAndPaymentId(GatewayName.PAYPAL, captureId);
        }

        if (paymentOpt.isEmpty()) {
            log.warn("Payment not found for PayPal capture: {} (order: {})", captureId, orderId);
            processedWebhook.markFailed("Payment not found for capture: " + captureId);
            return;
        }

        Payment payment = paymentOpt.get();
        processedWebhook.setPaymentId(payment.getId());

        // Only fulfill if not already succeeded (idempotency)
        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            try {
                // Verify the amount matches
                String capturedAmount = resource.path("amount").path("value").asText();
                if (capturedAmount != null && !capturedAmount.isEmpty()) {
                    java.math.BigDecimal captured = new java.math.BigDecimal(capturedAmount);
                    java.math.BigDecimal expected = payment.getPrice();
                    if (captured.subtract(expected).abs().compareTo(new java.math.BigDecimal("0.01")) > 0) {
                        log.warn("Amount mismatch - Expected: {}, Captured: {}",
                                expected, captured);
                    }
                }

                // Fulfill the payment
                payment.setStatus(PaymentStatus.SUCCEEDED);
                paymentRepository.save(payment);
                paymentService.fullFillPayment(payment);

                log.info("PayPal payment {} fulfilled successfully", payment.getId());
                processedWebhook.markSuccess();

            } catch (Exception e) {
                log.error("Error fulfilling PayPal payment {}", payment.getId(), e);
                processedWebhook.markFailed("Error fulfilling payment: " + e.getMessage());
            }
        } else {
            log.info("Payment {} already succeeded, skipping fulfillment", payment.getId());
            processedWebhook.markSkipped();
        }
    }

    /**
     * Handle PAYMENT.CAPTURE.DENIED event
     */
    private void handleCaptureDenied(JsonNode resource, ProcessedPayPalWebhook processedWebhook) {
        String captureId = resource.path("id").asText();
        String orderId = extractOrderIdFromCapture(resource);

        log.warn("PayPal capture denied - Capture ID: {}, Order ID: {}", captureId, orderId);

        Optional<Payment> paymentOpt = paymentRepository.findByGatewayAndPaymentId(
                GatewayName.PAYPAL, orderId != null ? orderId : captureId);

        if (paymentOpt.isPresent()) {
            Payment payment = paymentOpt.get();
            processedWebhook.setPaymentId(payment.getId());

            payment.setStatus(PaymentStatus.FAILED);
            payment.setErrorMessage("Payment capture denied by PayPal");
            paymentRepository.save(payment);

            processedWebhook.markSuccess();
        } else {
            processedWebhook.markFailed("Payment not found");
        }
    }

    /**
     * Handle PAYMENT.CAPTURE.REFUNDED event
     */
    private void handleCaptureRefunded(JsonNode resource, ProcessedPayPalWebhook processedWebhook) {
        String captureId = resource.path("id").asText();
        log.info("PayPal capture refunded: {}", captureId);

        // Find and update the payment
        Optional<Payment> paymentOpt = paymentRepository.findByGatewayAndPaymentId(
                GatewayName.PAYPAL, captureId);

        if (paymentOpt.isPresent()) {
            Payment payment = paymentOpt.get();
            processedWebhook.setPaymentId(payment.getId());

            payment.setStatus(PaymentStatus.REFUNDED);
            paymentRepository.save(payment);

            // Sync invoice status
            invoiceService.updateInvoiceStatusFromPayment(payment);

            log.info("Payment {} marked as refunded", payment.getId());
            processedWebhook.markSuccess();
        } else {
            log.warn("Payment not found for refunded capture: {}", captureId);
            processedWebhook.markFailed("Payment not found");
        }
    }

    /**
     * Extract order ID from capture resource
     */
    private String extractOrderIdFromCapture(JsonNode resource) {
        // Try supplementary_data first
        String orderId = resource.path("supplementary_data")
                .path("related_ids")
                .path("order_id")
                .asText(null);

        // Try links if not in supplementary_data
        if (orderId == null || orderId.isEmpty()) {
            JsonNode links = resource.path("links");
            if (links.isArray()) {
                for (JsonNode link : links) {
                    if ("up".equals(link.path("rel").asText())) {
                        String href = link.path("href").asText();
                        // Extract order ID from URL like /v2/checkout/orders/{order_id}
                        if (href.contains("/orders/")) {
                            orderId = href.substring(href.lastIndexOf("/orders/") + 8);
                            if (orderId.contains("/")) {
                                orderId = orderId.substring(0, orderId.indexOf("/"));
                            }
                        }
                        break;
                    }
                }
            }
        }

        return orderId;
    }

    /**
     * Extract resource ID based on event type
     */
    private String extractResourceId(JsonNode resource, String eventType) {
        return resource.path("id").asText();
    }

    /**
     * Verify PayPal webhook signature
     * Based on PayPal's webhook signature verification algorithm
     */
    private boolean verifyWebhookSignature(String transmissionId, String transmissionTime,
            String webhookId, String signature, String certUrl, String authAlgo, String payload) {

        if (transmissionId == null || transmissionTime == null || signature == null) {
            log.warn("Missing PayPal webhook signature headers");
            return false;
        }

        if (certUrl == null || certUrl.isEmpty()) {
            log.warn("Missing PayPal certificate URL");
            return false;
        }

        try {
            // Validate certUrl is from PayPal domain
            if (!isValidPayPalCertUrl(certUrl)) {
                log.error("Invalid PayPal certificate URL: {}", certUrl);
                return false;
            }

            // Construct the expected signature string
            // Format: transmissionId|transmissionTime|webhookId|crc32(payload)
            long crc32 = computeCrc32(payload);
            String expectedSignatureString = String.format("%s|%s|%s|%d",
                    transmissionId, transmissionTime, webhookId, crc32);

            log.debug("PayPal signature verification string: {}", expectedSignatureString);

            // Get certificate (from cache or fetch)
            X509Certificate certificate = getCertificate(certUrl);
            if (certificate == null) {
                log.error("Failed to obtain PayPal certificate");
                return false;
            }

            // Determine signature algorithm
            String algorithm = mapAuthAlgoToJavaAlgo(authAlgo);

            // Verify signature
            Signature sig = Signature.getInstance(algorithm);
            sig.initVerify(certificate.getPublicKey());
            sig.update(expectedSignatureString.getBytes(StandardCharsets.UTF_8));

            byte[] signatureBytes = Base64.getDecoder().decode(signature);
            boolean isValid = sig.verify(signatureBytes);

            if (!isValid) {
                log.warn("PayPal webhook signature verification failed");
            } else {
                log.debug("PayPal webhook signature verified successfully");
            }

            return isValid;

        } catch (Exception e) {
            log.error("Error verifying PayPal webhook signature", e);
            return false;
        }
    }

    /**
     * Validate that the certificate URL is from PayPal's domain
     */
    private boolean isValidPayPalCertUrl(String certUrl) {
        try {
            URI uri = URI.create(certUrl);
            String host = uri.getHost();
            return host != null && (
                    host.endsWith(".paypal.com") ||
                    host.endsWith(".paypalobjects.com")
            ) && "https".equalsIgnoreCase(uri.getScheme());
        } catch (Exception e) {
            log.error("Invalid certificate URL format: {}", certUrl);
            return false;
        }
    }

    /**
     * Get certificate from cache or fetch from URL
     */
    private X509Certificate getCertificate(String certUrl) {
        return certificateCache.computeIfAbsent(certUrl, url -> {
            try {
                log.debug("Fetching PayPal certificate from: {}", url);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    log.error("Failed to fetch PayPal certificate. Status: {}", response.statusCode());
                    return null;
                }

                String certPem = response.body();
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                InputStream certStream = new ByteArrayInputStream(certPem.getBytes(StandardCharsets.UTF_8));
                X509Certificate cert = (X509Certificate) cf.generateCertificate(certStream);

                // Validate certificate
                cert.checkValidity();

                log.info("PayPal certificate loaded and cached: {}", cert.getSubjectX500Principal());
                return cert;

            } catch (Exception e) {
                log.error("Error fetching PayPal certificate from {}", url, e);
                return null;
            }
        });
    }

    /**
     * Map PayPal auth algorithm to Java signature algorithm
     */
    private String mapAuthAlgoToJavaAlgo(String authAlgo) {
        if (authAlgo == null) {
            return "SHA256withRSA"; // Default
        }

        return switch (authAlgo.toUpperCase()) {
            case "SHA256WITHRSA" -> "SHA256withRSA";
            case "SHA1WITHRSA" -> "SHA1withRSA";
            case "SHA384WITHRSA" -> "SHA384withRSA";
            case "SHA512WITHRSA" -> "SHA512withRSA";
            default -> {
                log.warn("Unknown PayPal auth algorithm: {}, using SHA256withRSA", authAlgo);
                yield "SHA256withRSA";
            }
        };
    }

    /**
     * Compute CRC32 checksum of payload
     */
    private long computeCrc32(String payload) {
        java.util.zip.CRC32 crc32 = new java.util.zip.CRC32();
        crc32.update(payload.getBytes(StandardCharsets.UTF_8));
        return crc32.getValue();
    }
}
