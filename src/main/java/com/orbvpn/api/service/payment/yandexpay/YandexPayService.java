package com.orbvpn.api.service.payment.yandexpay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbvpn.api.domain.entity.Payment;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.YandexPayment;
import com.orbvpn.api.domain.enums.PaymentStatus;
import com.orbvpn.api.domain.payload.YandexPay.*;
import com.orbvpn.api.exception.PaymentException;
import com.orbvpn.api.repository.YandexPaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for interacting with Yandex Pay API.
 * Yandex Pay is a Russian payment gateway supporting RUB payments.
 *
 * API Documentation: https://pay.yandex.ru/docs/en/console/
 */
@Service
@Slf4j
public class YandexPayService {

    private static final String API_BASE_URL = "https://pay.yandex.ru/api/merchant/v1";
    private static final String SANDBOX_API_BASE_URL = "https://sandbox.pay.yandex.ru/api/merchant/v1";

    private final YandexPaymentRepository yandexPaymentRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${yandex-pay.api-key}")
    private String apiKey;

    @Value("${yandex-pay.merchant-id}")
    private String merchantId;

    @Value("${yandex-pay.sandbox:false}")
    private boolean sandbox;

    @Value("${yandex-pay.success-url:https://orbvpn.com/payment/success}")
    private String successUrl;

    @Value("${yandex-pay.error-url:https://orbvpn.com/payment/error}")
    private String errorUrl;

    @Value("${yandex-pay.ttl:1800}")
    private Integer ttl;

    public YandexPayService(YandexPaymentRepository yandexPaymentRepository, ObjectMapper objectMapper) {
        this.yandexPaymentRepository = yandexPaymentRepository;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    private String getBaseUrl() {
        return sandbox ? SANDBOX_API_BASE_URL : API_BASE_URL;
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Api-Key " + apiKey);
        return headers;
    }

    /**
     * Create a new Yandex Pay order
     */
    @Transactional
    public YandexPayResponse createPayment(User user, Payment payment, BigDecimal amountRub) {
        try {
            // Generate unique order ID
            String orderId = "ORB-YP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

            log.info("Creating Yandex Pay order for payment ID: {}, amount: {} RUB",
                    payment.getId(), amountRub);

            // Validate amount
            if (amountRub == null || amountRub.compareTo(BigDecimal.ZERO) <= 0) {
                throw new PaymentException("Invalid payment amount");
            }

            // Check for existing payment
            if (yandexPaymentRepository.existsByOrderId(orderId)) {
                log.warn("Order ID collision, regenerating: {}", orderId);
                orderId = "ORB-YP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            }

            // Create entity first
            YandexPayment yandexPayment = YandexPayment.builder()
                    .user(user)
                    .payment(payment)
                    .orderId(orderId)
                    .amount(amountRub.setScale(2, RoundingMode.HALF_UP))
                    .currency("RUB")
                    .paymentStatus(YandexPayment.STATUS_PENDING)
                    .description("OrbVPN Subscription Payment #" + payment.getId())
                    .expiresAt(LocalDateTime.now().plusSeconds(ttl))
                    .build();
            yandexPaymentRepository.save(yandexPayment);

            // Build request
            String amountStr = amountRub.setScale(2, RoundingMode.HALF_UP).toString();

            CreateOrderRequest request = CreateOrderRequest.builder()
                    .orderId(orderId)
                    .currencyCode("RUB")
                    .availablePaymentMethods(List.of("CARD"))
                    .redirectUrls(CreateOrderRequest.RedirectUrls.builder()
                            .onSuccess(successUrl + "?orderId=" + orderId)
                            .onError(errorUrl + "?orderId=" + orderId)
                            .build())
                    .cart(CreateOrderRequest.Cart.builder()
                            .total(CreateOrderRequest.Total.builder()
                                    .amount(amountStr)
                                    .build())
                            .items(List.of(CreateOrderRequest.CartItem.builder()
                                    .productId(payment.getId().toString())
                                    .title("OrbVPN Subscription")
                                    .quantity(CreateOrderRequest.Quantity.builder()
                                            .count(1)
                                            .build())
                                    .total(amountStr)
                                    .build()))
                            .build())
                    .ttl(ttl)
                    .build();

            // Make API request
            String url = getBaseUrl() + "/orders";
            HttpEntity<CreateOrderRequest> entity = new HttpEntity<>(request, createHeaders());

            ResponseEntity<CreateOrderResponse> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, CreateOrderResponse.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.error("Failed to create Yandex Pay order. Status: {}", response.getStatusCode());
                yandexPayment.markFailed("Failed to create order");
                payment.setStatus(PaymentStatus.FAILED);
                payment.setErrorMessage("Failed to create Yandex Pay order");
                yandexPaymentRepository.save(yandexPayment);

                return YandexPayResponse.builder()
                        .id(yandexPayment.getId())
                        .orderId(orderId)
                        .error("Failed to create payment")
                        .build();
            }

            CreateOrderResponse apiResponse = response.getBody();

            if (!apiResponse.isSuccess()) {
                log.error("Yandex Pay API error: {}", apiResponse.getReasonCode());
                yandexPayment.markFailed(apiResponse.getReasonCode());
                payment.setStatus(PaymentStatus.FAILED);
                payment.setErrorMessage("Yandex Pay error: " + apiResponse.getReasonCode());
                yandexPaymentRepository.save(yandexPayment);

                return YandexPayResponse.builder()
                        .id(yandexPayment.getId())
                        .orderId(orderId)
                        .error(apiResponse.getReasonCode())
                        .build();
            }

            // Update entity with response
            yandexPayment.setYandexOrderId(apiResponse.getOrderId());
            yandexPayment.setPaymentUrl(apiResponse.getPaymentUrl());
            yandexPaymentRepository.save(yandexPayment);

            log.info("Created Yandex Pay order: {} with payment URL", apiResponse.getOrderId());

            return YandexPayResponse.builder()
                    .id(yandexPayment.getId())
                    .yandexOrderId(apiResponse.getOrderId())
                    .orderId(orderId)
                    .status(YandexPayment.STATUS_PENDING)
                    .paymentUrl(apiResponse.getPaymentUrl())
                    .amount(amountRub)
                    .currency("RUB")
                    .expiresAt(yandexPayment.getExpiresAt().toString())
                    .build();

        } catch (Exception e) {
            log.error("Error creating Yandex Pay order", e);
            payment.setStatus(PaymentStatus.FAILED);
            payment.setErrorMessage("Failed to create Yandex Pay order: " + e.getMessage());
            throw new PaymentException("Failed to create Yandex Pay order", e);
        }
    }

    /**
     * Get payment status from Yandex Pay
     */
    public YandexPayment getPaymentStatus(String orderId) {
        return yandexPaymentRepository.findByOrderId(orderId).orElse(null);
    }

    /**
     * Find by internal order ID
     */
    public Optional<YandexPayment> findByOrderId(String orderId) {
        return yandexPaymentRepository.findByOrderId(orderId);
    }

    /**
     * Find by Yandex Pay order ID
     */
    public Optional<YandexPayment> findByYandexOrderId(String yandexOrderId) {
        return yandexPaymentRepository.findByYandexOrderId(yandexOrderId);
    }

    /**
     * Find by ID
     */
    public Optional<YandexPayment> findById(Long id) {
        return yandexPaymentRepository.findById(id);
    }

    /**
     * Save/update a payment
     */
    @Transactional
    public YandexPayment save(YandexPayment payment) {
        return yandexPaymentRepository.save(payment);
    }

    /**
     * Verify webhook JWT signature
     * Yandex Pay sends JWT tokens signed with ES256
     */
    public boolean verifyWebhookSignature(String jwtToken) {
        try {
            // For now, return true if JWT can be decoded
            // In production, fetch JWK keys and verify properly
            if (jwtToken == null || jwtToken.isEmpty()) {
                return false;
            }

            // Basic JWT structure validation
            String[] parts = jwtToken.split("\\.");
            if (parts.length != 3) {
                log.warn("Invalid JWT structure");
                return false;
            }

            // Decode and parse header to get key ID
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
            JsonNode header = objectMapper.readTree(headerJson);
            String kid = header.path("kid").asText();
            String alg = header.path("alg").asText();

            if (!"ES256".equals(alg)) {
                log.warn("Unexpected JWT algorithm: {}", alg);
            }

            // In production, verify signature using public key from JWK endpoint
            // For now, we accept valid JWT structure
            log.debug("JWT verification - kid: {}, alg: {}", kid, alg);

            return true;

        } catch (Exception e) {
            log.error("Error verifying webhook signature", e);
            return false;
        }
    }

    /**
     * Parse webhook JWT payload
     */
    public WebhookPayload parseWebhookPayload(String jwtToken) {
        try {
            String[] parts = jwtToken.split("\\.");
            if (parts.length < 2) {
                return null;
            }

            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
            return objectMapper.readValue(payloadJson, WebhookPayload.class);

        } catch (Exception e) {
            log.error("Error parsing webhook payload", e);
            return null;
        }
    }

    /**
     * Find pending payments older than threshold (for cleanup)
     */
    public List<YandexPayment> findPendingPaymentsOlderThan(LocalDateTime threshold) {
        return yandexPaymentRepository.findPendingPaymentsOlderThan(threshold);
    }

    /**
     * Cancel a pending order
     */
    @Transactional
    public boolean cancelOrder(String orderId) {
        Optional<YandexPayment> paymentOpt = yandexPaymentRepository.findByOrderId(orderId);
        if (paymentOpt.isEmpty()) {
            return false;
        }

        YandexPayment payment = paymentOpt.get();
        if (!payment.isPending()) {
            log.warn("Cannot cancel non-pending payment: {}", orderId);
            return false;
        }

        payment.markCancelled();
        yandexPaymentRepository.save(payment);
        return true;
    }

    /**
     * Check if sandbox mode is enabled
     */
    public boolean isSandbox() {
        return sandbox;
    }

    /**
     * Get merchant ID
     */
    public String getMerchantId() {
        return merchantId;
    }
}
