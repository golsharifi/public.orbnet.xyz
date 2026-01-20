package com.orbvpn.api.service.payment;

import com.orbvpn.api.domain.dto.ParspalApprovePaymentRequest;
import com.orbvpn.api.domain.dto.ParspalApprovePaymentResponse;
import com.orbvpn.api.domain.dto.ParspalCreatePaymentRequest;
import com.orbvpn.api.domain.dto.ParspalCreatePaymentResponse;
import com.orbvpn.api.domain.entity.Payment;
import com.orbvpn.api.domain.enums.GatewayName;
import com.orbvpn.api.domain.enums.PaymentStatus;
import com.orbvpn.api.exception.NotFoundException;
import com.orbvpn.api.exception.PaymentException;
import com.orbvpn.api.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

/**
 * Service for interacting with Parspal payment gateway API.
 * Parspal is a payment gateway supporting USD payments.
 */
@Service
@Slf4j
public class ParspalService {

    private static final String PAYMENT_REQUEST_ENDPOINT = "/payment/request";
    private static final String PAYMENT_VERIFY_ENDPOINT = "/payment/verify";
    private static final String SUCCESS_STATUS = "100";

    @Value("${parspal.url}")
    private String apiUrl;

    @Value("${parspal.api-key}")
    private String apiKey;

    @Value("${parspal.return-url}")
    private String returnUrl;

    private final PaymentRepository paymentRepository;
    private final RestTemplate restTemplate;

    public ParspalService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Create a new payment with Parspal
     *
     * @param payment The payment entity to create a Parspal payment for
     * @return ParspalCreatePaymentResponse with payment details
     * @throws PaymentException if payment creation fails
     */
    @Transactional
    public ParspalCreatePaymentResponse createPayment(Payment payment) {
        validatePayment(payment);

        log.info("Creating Parspal payment for payment ID: {}, amount: {}",
                payment.getId(), payment.getPrice());

        ParspalCreatePaymentRequest requestBody = buildCreatePaymentRequest(payment);
        HttpEntity<ParspalCreatePaymentRequest> request = new HttpEntity<>(requestBody, createHeaders());

        try {
            String url = normalizeUrl(apiUrl) + PAYMENT_REQUEST_ENDPOINT;
            log.debug("Sending Parspal payment request to: {}", url);

            ResponseEntity<ParspalCreatePaymentResponse> result =
                    restTemplate.postForEntity(url, request, ParspalCreatePaymentResponse.class);

            ParspalCreatePaymentResponse body = result.getBody();

            if (body == null) {
                log.error("Parspal returned null response body for payment ID: {}", payment.getId());
                payment.setStatus(PaymentStatus.FAILED);
                payment.setErrorMessage("Parspal returned empty response");
                paymentRepository.save(payment);
                throw new PaymentException("Parspal returned empty response");
            }

            // Check for API errors
            if (body.getError_type() != null || body.getError_code() != null) {
                String errorMsg = String.format("Parspal error: type=%s, code=%s, message=%s",
                        body.getError_type(), body.getError_code(), body.getMessage());
                log.error(errorMsg + " for payment ID: {}", payment.getId());
                payment.setStatus(PaymentStatus.FAILED);
                payment.setErrorMessage(errorMsg);
                paymentRepository.save(payment);
                throw new PaymentException(errorMsg);
            }

            // Check for missing payment_id
            if (body.getPayment_id() == null || body.getPayment_id().isEmpty()) {
                log.error("Parspal returned null payment_id for payment ID: {}", payment.getId());
                payment.setStatus(PaymentStatus.FAILED);
                payment.setErrorMessage("Parspal returned null payment_id");
                paymentRepository.save(payment);
                throw new PaymentException("Parspal returned null payment_id");
            }

            // Update payment with Parspal payment ID
            payment.setPaymentId(body.getPayment_id());
            paymentRepository.save(payment);

            log.info("Successfully created Parspal payment: {} for payment ID: {}",
                    body.getPayment_id(), payment.getId());

            return body;

        } catch (RestClientException e) {
            String errorMsg = "Failed to communicate with Parspal API: " + e.getMessage();
            log.error(errorMsg + " for payment ID: {}", payment.getId(), e);
            payment.setStatus(PaymentStatus.FAILED);
            payment.setErrorMessage(errorMsg);
            paymentRepository.save(payment);
            throw new PaymentException(errorMsg, e);
        }
    }

    /**
     * Approve/verify a payment with Parspal
     *
     * @param paymentId The Parspal payment ID
     * @param receipt   The receipt number from Parspal
     * @return true if payment was approved, false otherwise
     */
    @Transactional
    public boolean approvePayment(String paymentId, String receipt) {
        log.info("Approving Parspal payment: {} with receipt: {}", paymentId, receipt);

        if (paymentId == null || paymentId.isEmpty()) {
            log.warn("Attempted to approve payment with null/empty paymentId");
            return false;
        }

        if (receipt == null || receipt.isEmpty()) {
            log.warn("Attempted to approve payment {} with null/empty receipt", paymentId);
            return false;
        }

        // Find payment with pessimistic lock to prevent race conditions
        Payment payment = paymentRepository
                .findByGatewayAndPaymentId(GatewayName.PARSPAL, paymentId)
                .orElseThrow(() -> {
                    log.error("Payment not found for Parspal paymentId: {}", paymentId);
                    return new NotFoundException("Cannot find payment with ID: " + paymentId);
                });

        // Idempotency check - skip if already processed
        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            log.info("Payment {} already succeeded, skipping approval", paymentId);
            return true;
        }

        if (payment.getStatus() == PaymentStatus.FAILED) {
            log.warn("Attempted to approve already failed payment: {}", paymentId);
            return false;
        }

        ParspalApprovePaymentRequest approveRequest = buildApprovePaymentRequest(payment, receipt);
        HttpEntity<ParspalApprovePaymentRequest> request = new HttpEntity<>(approveRequest, createHeaders());

        try {
            String url = normalizeUrl(apiUrl) + PAYMENT_VERIFY_ENDPOINT;
            log.debug("Sending Parspal verify request to: {}", url);

            ResponseEntity<ParspalApprovePaymentResponse> response =
                    restTemplate.postForEntity(url, request, ParspalApprovePaymentResponse.class);

            ParspalApprovePaymentResponse approveResponse = response.getBody();

            if (approveResponse == null) {
                log.error("Parspal verify returned null response for paymentId: {}", paymentId);
                return false;
            }

            String status = approveResponse.getStatus();
            if (status == null) {
                log.error("Parspal verify returned null status for paymentId: {}", paymentId);
                return false;
            }

            boolean isApproved = SUCCESS_STATUS.equals(status);

            if (isApproved) {
                log.info("Parspal payment {} approved successfully", paymentId);
            } else {
                log.warn("Parspal payment {} not approved, status: {}", paymentId, status);
            }

            return isApproved;

        } catch (RestClientException e) {
            log.error("Failed to verify Parspal payment {}: {}", paymentId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Find payment by Parspal payment ID
     */
    public Optional<Payment> findByPaymentId(String paymentId) {
        return paymentRepository.findByGatewayAndPaymentId(GatewayName.PARSPAL, paymentId);
    }

    /**
     * Check if a payment exists
     */
    public boolean paymentExists(String paymentId) {
        return paymentRepository.findByGatewayAndPaymentId(GatewayName.PARSPAL, paymentId).isPresent();
    }

    private void validatePayment(Payment payment) {
        if (payment == null) {
            throw new PaymentException("Payment cannot be null");
        }
        if (payment.getPrice() == null || payment.getPrice().doubleValue() <= 0) {
            throw new PaymentException("Invalid payment amount");
        }
    }

    private ParspalCreatePaymentRequest buildCreatePaymentRequest(Payment payment) {
        ParspalCreatePaymentRequest request = new ParspalCreatePaymentRequest();
        request.setAmount(payment.getPrice().doubleValue());
        request.setReturnUrl(returnUrl);
        request.setOrderId(payment.getPaymentId());
        return request;
    }

    private ParspalApprovePaymentRequest buildApprovePaymentRequest(Payment payment, String receipt) {
        ParspalApprovePaymentRequest request = new ParspalApprovePaymentRequest();
        request.setAmount(payment.getPrice().doubleValue());
        request.setReceipt(receipt);
        return request;
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("APIKey", apiKey);
        headers.set("Content-Type", "application/json");
        return headers;
    }

    /**
     * Normalize URL to remove trailing slashes
     */
    private String normalizeUrl(String url) {
        if (url != null && url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }

    /**
     * Check if service is properly configured
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.equals("unknown") &&
               returnUrl != null && !returnUrl.equals("unknown") &&
               apiUrl != null && !apiUrl.isEmpty();
    }
}
