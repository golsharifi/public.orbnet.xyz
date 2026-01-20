package com.orbvpn.api.service.payment.nowpayment;

import com.orbvpn.api.domain.entity.NowPayment;
import com.orbvpn.api.domain.entity.Payment;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.enums.PaymentStatus;
import com.orbvpn.api.domain.payload.NowPayment.*;
import com.orbvpn.api.exception.PaymentException;
import com.orbvpn.api.repository.NowPaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Formatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for interacting with NOWPayments API.
 * NOWPayments is a cryptocurrency payment gateway supporting 300+ cryptocurrencies.
 *
 * API Documentation: https://documenter.getpostman.com/view/7907941/S1a32n38
 */
@Service
@Slf4j
public class NowPaymentService {

    private static final String API_BASE_URL = "https://api.nowpayments.io/v1";
    private static final String SANDBOX_API_BASE_URL = "https://api-sandbox.nowpayments.io/v1";

    private final NowPaymentRepository nowPaymentRepository;
    private final RestTemplate restTemplate;

    @Value("${nowpayment.api-key}")
    private String apiKey;

    @Value("${nowpayment.ipn-secret}")
    private String ipnSecret;

    @Value("${nowpayment.ipn-callback-url}")
    private String ipnCallbackUrl;

    @Value("${nowpayment.sandbox:false}")
    private boolean sandbox;

    public NowPaymentService(NowPaymentRepository nowPaymentRepository) {
        this.nowPaymentRepository = nowPaymentRepository;
        this.restTemplate = new RestTemplate();
    }

    private String getBaseUrl() {
        return sandbox ? SANDBOX_API_BASE_URL : API_BASE_URL;
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        return headers;
    }

    /**
     * Check API status
     */
    public boolean checkApiStatus() {
        try {
            String url = getBaseUrl() + "/status";
            HttpEntity<Void> entity = new HttpEntity<>(createHeaders());
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Failed to check NOWPayments API status", e);
            return false;
        }
    }

    /**
     * Get available currencies
     */
    public List<String> getAvailableCurrencies() {
        try {
            String url = getBaseUrl() + "/currencies";
            HttpEntity<Void> entity = new HttpEntity<>(createHeaders());
            ResponseEntity<CurrenciesResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, CurrenciesResponse.class);

            if (response.getBody() != null) {
                return response.getBody().getCurrencies();
            }
            return List.of();
        } catch (Exception e) {
            log.error("Failed to get available currencies from NOWPayments", e);
            return List.of();
        }
    }

    /**
     * Get minimum payment amount for a currency
     */
    public BigDecimal getMinimumAmount(String currency) {
        try {
            String url = getBaseUrl() + "/min-amount?currency_from=" + currency.toLowerCase() + "&currency_to=usd";
            HttpEntity<Void> entity = new HttpEntity<>(createHeaders());
            ResponseEntity<MinAmountResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, MinAmountResponse.class);

            if (response.getBody() != null) {
                return response.getBody().getMinAmount();
            }
            return BigDecimal.ZERO;
        } catch (Exception e) {
            log.error("Failed to get minimum amount for currency: {}", currency, e);
            return BigDecimal.ZERO;
        }
    }

    /**
     * Get estimated price for conversion
     */
    public EstimateResponse getEstimatedPrice(BigDecimal amount, String fromCurrency, String toCurrency) {
        try {
            String url = String.format("%s/estimate?amount=%s&currency_from=%s&currency_to=%s",
                    getBaseUrl(), amount, fromCurrency.toLowerCase(), toCurrency.toLowerCase());
            HttpEntity<Void> entity = new HttpEntity<>(createHeaders());
            ResponseEntity<EstimateResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, EstimateResponse.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to get estimated price", e);
            return null;
        }
    }

    /**
     * Create a new cryptocurrency payment
     */
    @Transactional
    public NowPaymentResponse createPayment(User user, Payment payment, String payCurrency) {
        try {
            // Generate unique order ID
            String orderId = "ORB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

            // Build the request
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .priceAmount(payment.getPrice())
                    .priceCurrency("usd")
                    .payCurrency(payCurrency.toLowerCase())
                    .ipnCallbackUrl(ipnCallbackUrl)
                    .orderId(orderId)
                    .orderDescription("OrbVPN Subscription Payment #" + payment.getId())
                    .isFixedRate(false)
                    .build();

            // Create entity first to save the relation
            NowPayment nowPayment = NowPayment.builder()
                    .user(user)
                    .payment(payment)
                    .orderId(orderId)
                    .priceAmount(payment.getPrice())
                    .priceCurrency("usd")
                    .payCurrency(payCurrency.toLowerCase())
                    .paymentStatus("creating")
                    .ipnCallbackUrl(ipnCallbackUrl)
                    .orderDescription(request.getOrderDescription())
                    .build();
            nowPaymentRepository.save(nowPayment);

            // Make API request
            String url = getBaseUrl() + "/payment";
            HttpEntity<CreatePaymentRequest> entity = new HttpEntity<>(request, createHeaders());

            ResponseEntity<CreatePaymentResponse> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, CreatePaymentResponse.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.error("Failed to create NOWPayments payment. Status: {}", response.getStatusCode());
                nowPayment.setPaymentStatus("failed");
                payment.setStatus(PaymentStatus.FAILED);
                payment.setErrorMessage("Failed to create cryptocurrency payment");
                nowPaymentRepository.save(nowPayment);

                return NowPaymentResponse.builder()
                        .id(nowPayment.getId())
                        .error("Failed to create payment")
                        .build();
            }

            CreatePaymentResponse apiResponse = response.getBody();

            // Update entity with response data
            nowPayment.setPaymentId(apiResponse.getPaymentId());
            nowPayment.setPaymentStatus(apiResponse.getPaymentStatus());
            nowPayment.setPayAddress(apiResponse.getPayAddress());
            nowPayment.setPayAmount(apiResponse.getPayAmount());
            nowPayment.setPurchaseId(apiResponse.getPurchaseId());
            nowPayment.setActuallyPaid(apiResponse.getActuallyPaid());
            nowPayment.setOutcomeAmount(apiResponse.getOutcomeAmount());
            nowPayment.setOutcomeCurrency(apiResponse.getOutcomeCurrency());

            // Set expiration (typically 20-30 minutes for crypto payments)
            if (apiResponse.getExpirationEstimateDate() != null) {
                // Parse the expiration date if available
                nowPayment.setExpiresAt(LocalDateTime.now().plusMinutes(30));
            } else {
                nowPayment.setExpiresAt(LocalDateTime.now().plusMinutes(30));
            }

            nowPaymentRepository.save(nowPayment);

            log.info("Created NOWPayments payment: {} for user {} with address {}",
                    apiResponse.getPaymentId(), user.getId(), apiResponse.getPayAddress());

            return NowPaymentResponse.builder()
                    .id(nowPayment.getId())
                    .paymentId(apiResponse.getPaymentId())
                    .status(apiResponse.getPaymentStatus())
                    .payAddress(apiResponse.getPayAddress())
                    .payAmount(apiResponse.getPayAmount())
                    .payCurrency(apiResponse.getPayCurrency())
                    .priceAmount(apiResponse.getPriceAmount())
                    .priceCurrency(apiResponse.getPriceCurrency())
                    .orderId(orderId)
                    .expiresAt(apiResponse.getExpirationEstimateDate())
                    .network(apiResponse.getNetwork())
                    .payinExtraId(apiResponse.getPayinExtraId())
                    .build();

        } catch (Exception e) {
            log.error("Error creating NOWPayments payment", e);
            payment.setStatus(PaymentStatus.FAILED);
            payment.setErrorMessage("Failed to create cryptocurrency payment: " + e.getMessage());
            throw new PaymentException("Failed to create NOWPayments payment", e);
        }
    }

    /**
     * Get payment status from NOWPayments
     */
    public PaymentStatusResponse getPaymentStatus(String paymentId) {
        try {
            String url = getBaseUrl() + "/payment/" + paymentId;
            HttpEntity<Void> entity = new HttpEntity<>(createHeaders());
            ResponseEntity<PaymentStatusResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, PaymentStatusResponse.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to get payment status for: {}", paymentId, e);
            return null;
        }
    }

    /**
     * Find payment by NOWPayments payment ID
     */
    public Optional<NowPayment> findByPaymentId(String paymentId) {
        return nowPaymentRepository.findByPaymentId(paymentId);
    }

    /**
     * Find payment by internal order ID
     */
    public Optional<NowPayment> findByOrderId(String orderId) {
        return nowPaymentRepository.findByOrderId(orderId);
    }

    /**
     * Find payment by internal ID
     */
    public Optional<NowPayment> findById(Long id) {
        return nowPaymentRepository.findById(id);
    }

    /**
     * Save/update a payment
     */
    @Transactional
    public NowPayment save(NowPayment payment) {
        return nowPaymentRepository.save(payment);
    }

    /**
     * Update payment status from IPN callback
     */
    @Transactional
    public void updatePaymentFromCallback(IpnCallbackPayload callback) {
        String orderId = callback.getOrderId();
        Optional<NowPayment> paymentOpt = nowPaymentRepository.findByOrderId(orderId);

        if (paymentOpt.isEmpty()) {
            log.warn("No payment found for order ID: {}", orderId);
            return;
        }

        NowPayment nowPayment = paymentOpt.get();
        nowPayment.setPaymentStatus(callback.getPaymentStatus());
        nowPayment.setActuallyPaid(callback.getActuallyPaid());
        nowPayment.setOutcomeAmount(callback.getOutcomeAmount());
        nowPayment.setOutcomeCurrency(callback.getOutcomeCurrency());

        nowPaymentRepository.save(nowPayment);

        log.info("Updated payment {} status to: {}", nowPayment.getId(), callback.getPaymentStatus());
    }

    /**
     * Verify IPN callback signature using HMAC-SHA512
     */
    public boolean verifyIpnSignature(String payload, String receivedSignature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    ipnSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            mac.init(secretKeySpec);

            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            // Convert to hex
            try (Formatter formatter = new Formatter()) {
                for (byte b : hash) {
                    formatter.format("%02x", b);
                }
                String calculatedSignature = formatter.toString();
                return calculatedSignature.equalsIgnoreCase(receivedSignature);
            }
        } catch (Exception e) {
            log.error("Error verifying IPN signature", e);
            return false;
        }
    }

    /**
     * Get IPN secret (for use in webhook controller)
     */
    public String getIpnSecret() {
        return ipnSecret;
    }

    /**
     * Find pending payments older than threshold (for cleanup)
     */
    public List<NowPayment> findPendingPaymentsOlderThan(LocalDateTime threshold) {
        return nowPaymentRepository.findPendingPaymentsOlderThan(threshold);
    }
}
