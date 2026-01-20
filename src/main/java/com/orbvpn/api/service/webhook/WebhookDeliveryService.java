package com.orbvpn.api.service.webhook;

import com.orbvpn.api.domain.entity.WebhookDelivery;
import com.orbvpn.api.domain.entity.WebhookDeliveryAttempt;
import com.orbvpn.api.repository.WebhookDeliveryAttemptRepository;
import com.orbvpn.api.repository.WebhookDeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@Slf4j
@RequiredArgsConstructor
public class WebhookDeliveryService {
    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookDeliveryAttemptRepository attemptRepository;
    private final RestTemplate restTemplate;
    private static final int MAX_RETRIES = 5;
    private static final Duration[] RETRY_DELAYS = {
            Duration.ofSeconds(30),
            Duration.ofMinutes(2),
            Duration.ofMinutes(5),
            Duration.ofMinutes(15),
            Duration.ofMinutes(30)
    };

    @Async
    public void deliverWebhook(WebhookDelivery delivery) {
        LocalDateTime start = LocalDateTime.now();
        WebhookDeliveryAttempt attempt = new WebhookDeliveryAttempt();
        attempt.setDelivery(delivery);

        try {
            // Prepare headers
            HttpHeaders headers = prepareHeaders(delivery);

            // Send request
            HttpEntity<String> request = new HttpEntity<>(delivery.getPayload(), headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    delivery.getWebhook().getEndpoint(),
                    request,
                    String.class);

            // Record response
            attempt.setStatusCode(response.getStatusCode().value());
            attempt.setResponseBody(response.getBody());
            attempt.setResponseStatus("SUCCESS");

            if (response.getStatusCode().is2xxSuccessful()) {
                delivery.setStatus("DELIVERED");
            } else {
                handleDeliveryError(delivery, attempt,
                        "Non-200 response: " + response.getStatusCode().value());
            }

        } catch (Exception e) {
            handleDeliveryError(delivery, attempt, e.getMessage());
        } finally {
            // Record timing
            attempt.setResponseTimeMs(
                    Duration.between(start, LocalDateTime.now()).toMillis());
            attemptRepository.save(attempt);
            deliveryRepository.save(delivery);
        }
    }

    private void handleDeliveryError(WebhookDelivery delivery,
            WebhookDeliveryAttempt attempt,
            String error) {
        attempt.setResponseStatus("FAILED");
        attempt.setErrorMessage(error);

        if (delivery.getRetryCount() < MAX_RETRIES) {
            scheduleRetry(delivery);
        } else {
            delivery.setStatus("FAILED");
        }
    }

    private void scheduleRetry(WebhookDelivery delivery) {
        int retryIndex = delivery.getRetryCount();
        delivery.setRetryCount(retryIndex + 1);
        delivery.setStatus("PENDING_RETRY");
        delivery.setNextAttempt(
                LocalDateTime.now().plus(RETRY_DELAYS[retryIndex]));
    }

    private HttpHeaders prepareHeaders(WebhookDelivery delivery) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("User-Agent", "OrbVPN-Webhook/1.0");
        headers.set("X-Webhook-ID", delivery.getId().toString());
        headers.set("X-Retry-Count", String.valueOf(delivery.getRetryCount()));

        String signature = generateSignature(
                delivery.getPayload(),
                delivery.getWebhook().getSecret());
        headers.set("X-Webhook-Signature", signature);

        return headers;
    }

    private String generateSignature(String payload, String secret) {
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    secret.getBytes(), "HmacSHA256");
            sha256_HMAC.init(secretKey);
            byte[] hash = sha256_HMAC.doFinal(payload.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.error("Error generating webhook signature", e);
            return "";
        }
    }
}