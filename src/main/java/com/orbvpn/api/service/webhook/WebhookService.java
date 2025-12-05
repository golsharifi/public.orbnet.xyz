package com.orbvpn.api.service.webhook;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbvpn.api.domain.entity.WebhookConfiguration;
import com.orbvpn.api.domain.entity.WebhookDelivery;
import com.orbvpn.api.domain.dto.WebhookConfigurationDTO;
import com.orbvpn.api.domain.dto.WebhookDeliveryDTO;
import com.orbvpn.api.event.UserActionEvent;
import com.orbvpn.api.event.UserDeletedEvent;
import com.orbvpn.api.exception.NotFoundException;
import com.orbvpn.api.repository.WebhookConfigurationRepository;
import com.orbvpn.api.repository.WebhookDeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class WebhookService {
    private final WebhookConfigurationRepository webhookConfigurationRepository;
    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    private static final int MAX_RETRIES = 3;
    private static final long[] RETRY_DELAYS = { 30, 120, 300 }; // Delays in seconds

    @Value("${spring.profiles.active:unknown}")
    private String environment;

    // CRUD Operations
    public WebhookConfigurationDTO createConfiguration(WebhookConfigurationDTO dto) {
        WebhookConfiguration config = new WebhookConfiguration();
        updateConfigurationFromDTO(config, dto);

        WebhookConfiguration saved = webhookConfigurationRepository.save(config);
        return convertToDTO(saved);
    }

    public WebhookConfigurationDTO updateConfiguration(Long id, WebhookConfigurationDTO dto) {
        WebhookConfiguration config = webhookConfigurationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Webhook configuration not found"));

        updateConfigurationFromDTO(config, dto);
        WebhookConfiguration saved = webhookConfigurationRepository.save(config);
        return convertToDTO(saved);
    }

    public Boolean deleteConfiguration(Long id) {
        webhookConfigurationRepository.deleteById(id);
        return true;
    }

    public WebhookConfigurationDTO getConfiguration(Long id) {
        WebhookConfiguration config = webhookConfigurationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Webhook configuration not found"));
        return convertToDTO(config);
    }

    public List<WebhookConfigurationDTO> getAllConfigurations() {
        List<WebhookConfiguration> configs = webhookConfigurationRepository.findAll();
        List<WebhookConfigurationDTO> dtos = new ArrayList<>();
        for (WebhookConfiguration config : configs) {
            dtos.add(convertToDTO(config));
        }
        return dtos;
    }

    public List<WebhookDeliveryDTO> getDeliveries(Long configId, int page, int size) {
        Page<WebhookDelivery> deliveriesPage = webhookDeliveryRepository.findByWebhookId(
                configId,
                PageRequest.of(page, size));

        List<WebhookDeliveryDTO> deliveryDTOs = new ArrayList<>();
        for (WebhookDelivery delivery : deliveriesPage.getContent()) {
            deliveryDTOs.add(convertToDeliveryDTO(delivery));
        }
        return deliveryDTOs;
    }

    // Event Handlers
    @EventListener
    public void handleUserActionEvent(UserActionEvent event) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("userId", event.getUser().getId());
            payload.put("username", event.getUser().getUsername());
            payload.put("action", event.getAction());

            processWebhook("USER_ACTION", payload);
        } catch (Exception e) {
            log.error("Error handling user action event", e);
        }
    }

    @EventListener
    public void handleUserDeletedEvent(UserDeletedEvent event) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("userId", event.getUser().getId());
            payload.put("username", event.getUser().getUsername());

            // processWebhook("USER_DELETED", payload);
        } catch (Exception e) {
            log.error("Error handling user deleted event", e);
        }
    }

    // Webhook Processing
    public void processWebhook(String eventType, Map<String, Object> payload) {
        List<WebhookConfiguration> configs = webhookConfigurationRepository.findByActive(true);
        for (WebhookConfiguration config : configs) {
            if (config.getSubscribedEvents().contains(eventType)) {
                processWebhook(eventType, payload, config);
            }
        }
    }

    private void processWebhook(String eventType, Map<String, Object> payload, WebhookConfiguration config) {
        try {
            String formattedPayload;

            switch (config.getProviderType()) {
                case SLACK:
                    formattedPayload = formatSlackPayload(eventType, payload, config);
                    break;
                case GOHIGHLEVEL:
                    formattedPayload = formatGohighlevelPayload(eventType, payload, config);
                    break;
                case ZAPIER:
                    formattedPayload = formatZapierPayload(eventType, payload, config);
                    break;
                default:
                    formattedPayload = objectMapper.writeValueAsString(payload);
                    break;
            }

            if (formattedPayload == null) {
                throw new IllegalArgumentException("Failed to format payload");
            }

            WebhookDelivery delivery = new WebhookDelivery();
            delivery.setWebhook(config);
            delivery.setEventType(eventType);
            delivery.setPayload(formattedPayload);
            delivery.setStatus("PENDING");
            delivery.setCreatedAt(LocalDateTime.now());
            delivery.setRetryCount(0);

            delivery = webhookDeliveryRepository.save(delivery);
            sendWebhookAsync(delivery);
        } catch (Exception e) {
            log.error("Error processing webhook for event {} to endpoint {}: {}",
                    eventType, config.getEndpoint(), e.getMessage());
        }
    }

    private String formatSlackPayload(String eventType, Map<String, Object> payload, WebhookConfiguration config) {
        try {
            Map<String, Object> slackPayload = new HashMap<>();

            // Build message text
            StringBuilder message = new StringBuilder();
            message.append("*New Event: ").append(eventType).append("*\n\n");

            // Event Header with timestamp
            message.append("🔔 *").append(eventType).append("*\n")
                    .append("Time: ").append(payload.getOrDefault("timestamp", "")).append("\n\n");

            // User Basic Info Section
            message.append("👤 *User Information*\n")
                    .append("-------------------\n")
                    .append("• Email: ").append(payload.getOrDefault("email", "")).append("\n")
                    .append("• Username: ").append(payload.getOrDefault("username", "")).append("\n")
                    .append("• User ID: ").append(payload.getOrDefault("userId", "")).append("\n\n");

            // Profile Section - Only show if has meaningful data
            Map<String, Object> profile = safeGetMap(payload, "profile");
            if (!profile.isEmpty()) {
                String firstName = String.valueOf(profile.getOrDefault("firstName", ""));
                String lastName = String.valueOf(profile.getOrDefault("lastName", ""));
                String phone = String.valueOf(profile.getOrDefault("phone", ""));
                String address = String.valueOf(profile.getOrDefault("address", ""));
                String city = String.valueOf(profile.getOrDefault("city", ""));
                String country = String.valueOf(profile.getOrDefault("country", ""));
                String postalCode = String.valueOf(profile.getOrDefault("postalCode", ""));
                String birthDate = String.valueOf(profile.getOrDefault("birthDate", ""));

                if (!firstName.isEmpty() || !lastName.isEmpty() || !phone.isEmpty() || !country.isEmpty()) {
                    message.append("📋 *Profile Details*\n")
                            .append("-------------------\n");
                    if (!firstName.isEmpty() || !lastName.isEmpty()) {
                        message.append("• Name: ").append(firstName).append(" ").append(lastName).append("\n");
                    }
                    if (!phone.isEmpty()) {
                        message.append("• Phone: ").append(phone).append("\n");
                    }
                    if (!address.isEmpty()) {
                        message.append("• Address: ").append(address).append("\n");
                    }
                    if (!city.isEmpty()) {
                        message.append("• City: ").append(city).append("\n");
                    }
                    if (!country.isEmpty()) {
                        message.append("• Country: ").append(country).append("\n");
                    }
                    if (!postalCode.isEmpty()) {
                        message.append("• Postal Code: ").append(postalCode).append("\n");
                    }
                    if (!birthDate.isEmpty()) {
                        message.append("• Birth Date: ").append(birthDate).append("\n");
                    }
                    message.append("\n");
                }
            }

            // Add subscription section if exists
            Map<String, Object> subscription = safeGetMap(payload, "subscription");
            if (!subscription.isEmpty()) {
                Map<String, Object> group = safeGetMap(subscription, "group");

                message.append("*Subscription Details*\n");
                message.append("• Group ID: ").append(group.getOrDefault("id", "")).append("\n");
                message.append("• Group: ").append(group.getOrDefault("name", "")).append("\n");
                message.append("• Duration: ").append(subscription.getOrDefault("duration", "")).append(" days\n");
                message.append("• Multi-Login Count: ").append(subscription.getOrDefault("multiLoginCount", ""))
                        .append("\n");
                message.append("• Expires At: ").append(subscription.getOrDefault("expiresAt", "")).append("\n");
                message.append("• Daily Bandwidth: ").append(subscription.getOrDefault("dailyBandwidth", ""));
                message.append("• Download/Upload: ").append(subscription.getOrDefault("downloadUpload", ""))
                        .append("\n\n");
            }

            // Add remaining payload data
            for (Map.Entry<String, Object> entry : payload.entrySet()) {
                if (!entry.getKey().equals("profile") && !entry.getKey().equals("subscription")) {
                    message.append("*").append(entry.getKey()).append("*: ")
                            .append(entry.getValue())
                            .append("\n");
                }
            }

            // Set required text field
            slackPayload.put("text", message.toString());

            // Add provider specific config if it exists
            try {
                String providerConfig = config.getProviderSpecificConfig();
                if (providerConfig != null && !providerConfig.isEmpty()) {
                    Map<String, Object> specificConfig = objectMapper.readValue(providerConfig,
                            new TypeReference<Map<String, Object>>() {
                            });
                    slackPayload.putAll(specificConfig);
                }
            } catch (Exception e) {
                log.warn("Error parsing provider specific config for Slack: {}", e.getMessage());
            }

            return objectMapper.writeValueAsString(slackPayload);
        } catch (Exception e) {
            log.error("Error formatting Slack payload: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> safeGetMap(Map<String, Object> map, String key) {
        if (map.containsKey(key) && map.get(key) instanceof Map) {
            return (Map<String, Object>) map.get(key);
        }
        return new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    private String formatGohighlevelPayload(String eventType, Map<String, Object> payload,
            WebhookConfiguration config) {
        try {
            Map<String, Object> ghlPayload = new HashMap<>();
            Map<String, Object> contact = new HashMap<>();
            Map<String, Object> customFields = new HashMap<>();

            // Extract and flatten profile data
            if (payload.containsKey("profile")) {
                Map<String, Object> profile = (Map<String, Object>) payload.get("profile");
                // Basic contact fields
                contact.put("firstName", profile.getOrDefault("firstName", ""));
                contact.put("lastName", profile.getOrDefault("lastName", ""));
                contact.put("phone", profile.getOrDefault("phone", ""));
                contact.put("address1", profile.getOrDefault("address", ""));
                contact.put("city", profile.getOrDefault("city", ""));
                contact.put("country", profile.getOrDefault("country", ""));
                contact.put("postalCode", profile.getOrDefault("postalCode", ""));

                // Profile data as custom fields for better tracking
                customFields.put("user_birthDate", profile.getOrDefault("birthDate", ""));
                customFields.put("user_fullAddress", String.format("%s, %s, %s, %s",
                        profile.getOrDefault("address", ""),
                        profile.getOrDefault("city", ""),
                        profile.getOrDefault("country", ""),
                        profile.getOrDefault("postalCode", "")));
            }

            // Extract and flatten subscription data
            if (payload.containsKey("subscription")) {
                Map<String, Object> subscription = (Map<String, Object>) payload.get("subscription");
                Map<String, Object> group = subscription.containsKey("group")
                        ? (Map<String, Object>) subscription.get("group")
                        : new HashMap<>();

                // Subscription data as custom fields
                customFields.put("subscription_group", group.getOrDefault("name", ""));
                customFields.put("subscription_duration", subscription.getOrDefault("duration", ""));
                customFields.put("subscription_multiLogin", subscription.getOrDefault("multiLoginCount", ""));
                customFields.put("subscription_expiresAt", subscription.getOrDefault("expiresAt", ""));
                customFields.put("subscription_bandwidth", subscription.getOrDefault("dailyBandwidth", ""));
                customFields.put("subscription_downloadUpload", subscription.getOrDefault("downloadUpload", ""));

                // Calculate subscription status
                String expiresAt = (String) subscription.getOrDefault("expiresAt", "");
                if (!expiresAt.isEmpty()) {
                    LocalDateTime expiryDate = LocalDateTime.parse(expiresAt);
                    LocalDateTime now = LocalDateTime.now();
                    if (expiryDate.isBefore(now)) {
                        customFields.put("subscription_status", "EXPIRED");
                    } else if (expiryDate.minusDays(7).isBefore(now)) {
                        customFields.put("subscription_status", "EXPIRING_SOON");
                    } else {
                        customFields.put("subscription_status", "ACTIVE");
                    }
                }
            }

            contact.put("email", payload.getOrDefault("email", ""));
            // Store username as a custom field
            customFields.put("user_name", payload.getOrDefault("username", ""));
            customFields.put("user_id", payload.getOrDefault("userId", ""));
            customFields.put("user_lastUpdated", LocalDateTime.now().toString());

            // Add standard GHL fields
            ghlPayload.put("eventType", eventType);
            ghlPayload.put("timestamp", LocalDateTime.now().toString());
            ghlPayload.put("contact", contact);
            ghlPayload.put("customFields", customFields);

            List<String> tags = new ArrayList<>();
            if (payload.containsKey("subscription")) {
                Map<String, Object> subscription = (Map<String, Object>) payload.get("subscription");
                if (subscription.containsKey("expiresAt")) {
                    LocalDateTime expiresAt = LocalDateTime.parse((String) subscription.get("expiresAt"));
                    if (expiresAt.isBefore(LocalDateTime.now())) {
                        tags.add("Subscription Expired");
                    } else if (expiresAt.minusDays(7).isBefore(LocalDateTime.now())) {
                        tags.add("Subscription Expiring Soon");
                    }
                }
            }
            ghlPayload.put("tags", tags);

            // Create data object
            Map<String, Object> data = new HashMap<>(payload);
            ghlPayload.put("data", data);

            // Add provider specific config
            try {
                String providerConfig = config.getProviderSpecificConfig();
                if (providerConfig != null && !providerConfig.isEmpty()) {
                    Map<String, Object> specificConfig = objectMapper.readValue(providerConfig,
                            new TypeReference<Map<String, Object>>() {
                            });
                    ghlPayload.putAll(specificConfig);
                }
            } catch (Exception e) {
                log.warn("Error parsing provider specific config for GoHighLevel: {}", e.getMessage());
            }

            return objectMapper.writeValueAsString(ghlPayload);
        } catch (Exception e) {
            log.error("Error formatting GoHighLevel payload: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String formatZapierPayload(String eventType, Map<String, Object> payload, WebhookConfiguration config) {
        try {
            Map<String, Object> zapierPayload = new HashMap<>(payload);

            // Add standard Zapier fields
            zapierPayload.put("event_type", eventType);
            zapierPayload.put("occurred_at", LocalDateTime.now().toString());

            // Add user basic info
            zapierPayload.put("user_id", payload.getOrDefault("userId", ""));
            zapierPayload.put("email", payload.getOrDefault("email", ""));
            zapierPayload.put("username", payload.getOrDefault("username", ""));

            // Add profile data in a structured way
            if (payload.containsKey("profile")) {
                Map<String, Object> profile = (Map<String, Object>) payload.get("profile");
                Map<String, Object> userProfile = new HashMap<>();
                userProfile.put("first_name", profile.getOrDefault("firstName", ""));
                userProfile.put("last_name", profile.getOrDefault("lastName", ""));
                userProfile.put("phone", profile.getOrDefault("phone", ""));
                userProfile.put("address", profile.getOrDefault("address", ""));
                userProfile.put("city", profile.getOrDefault("city", ""));
                userProfile.put("country", profile.getOrDefault("country", ""));
                userProfile.put("postal_code", profile.getOrDefault("postalCode", ""));
                userProfile.put("birth_date", profile.getOrDefault("birthDate", ""));
                zapierPayload.put("profile", userProfile);
            }

            // Add subscription data in a structured way
            if (payload.containsKey("subscription")) {
                Map<String, Object> subscription = (Map<String, Object>) payload.get("subscription");
                Map<String, Object> subscriptionData = new HashMap<>();

                Map<String, Object> group = subscription.containsKey("group")
                        ? (Map<String, Object>) subscription.get("group")
                        : new HashMap<>();

                subscriptionData.put("group_name", group.getOrDefault("name", ""));
                subscriptionData.put("duration_days", subscription.getOrDefault("duration", ""));
                subscriptionData.put("multi_login_count", subscription.getOrDefault("multiLoginCount", ""));
                subscriptionData.put("expires_at", subscription.getOrDefault("expiresAt", ""));
                subscriptionData.put("daily_bandwidth", subscription.getOrDefault("dailyBandwidth", ""));
                subscriptionData.put("download_upload", subscription.getOrDefault("downloadUpload", ""));
                zapierPayload.put("subscription", subscriptionData);
            }
            // Add provider specific config
            try {
                String providerConfig = config.getProviderSpecificConfig();
                if (providerConfig != null && !providerConfig.isEmpty()) {
                    Map<String, Object> specificConfig = objectMapper.readValue(providerConfig,
                            new TypeReference<Map<String, Object>>() {
                            });
                    zapierPayload.putAll(specificConfig);
                }
            } catch (Exception e) {
                log.warn("Error parsing provider specific config for Zapier: {}", e.getMessage());
            }

            return objectMapper.writeValueAsString(zapierPayload);
        } catch (Exception e) {
            log.error("Error formatting Zapier payload: {}", e.getMessage());
            return null;
        }
    }

    @Async
    protected void sendWebhookAsync(WebhookDelivery delivery) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            headers.set("X-Webhook-ID", delivery.getId().toString());
            headers.set("X-Event-Type", delivery.getEventType());
            headers.set("X-Delivery-Attempt", String.valueOf(delivery.getRetryCount() + 1));

            if (delivery.getWebhook().getSecret() != null) {
                String signature = generateSignature(delivery.getPayload(), delivery.getWebhook().getSecret());
                headers.set("X-Webhook-Signature", signature);
            }

            HttpEntity<String> request = new HttpEntity<>(delivery.getPayload(), headers);

            LocalDateTime startTime = LocalDateTime.now();
            ResponseEntity<String> response = restTemplate.postForEntity(
                    delivery.getWebhook().getEndpoint(),
                    request,
                    String.class);
            Duration requestDuration = Duration.between(startTime, LocalDateTime.now());

            delivery.setLastAttempt(LocalDateTime.now());

            if (response.getStatusCode().is2xxSuccessful()) {
                handleSuccessfulDelivery(delivery, response, requestDuration);
            } else {
                handleFailedDelivery(delivery,
                        String.format("Non-200 response: %d", response.getStatusCode().value()),
                        requestDuration);
            }

        } catch (Exception e) {
            handleFailedDelivery(delivery, e.getMessage(), null);
        }

        webhookDeliveryRepository.save(delivery);
    }

    // Helper Methods
    public Boolean testConfiguration(Long id) {
        try {
            WebhookConfiguration config = webhookConfigurationRepository.findById(id)
                    .orElseThrow(() -> new NotFoundException("Webhook configuration not found"));

            Map<String, Object> testPayload = new HashMap<>();
            testPayload.put("test", true);
            testPayload.put("message", "Test webhook configuration");
            testPayload.put("timestamp", LocalDateTime.now().toString());

            processWebhook("TEST_EVENT", testPayload, config);
            return true;
        } catch (Exception e) {
            log.error("Error testing webhook configuration: {}", e.getMessage());
            return false;
        }
    }

    private void handleSuccessfulDelivery(WebhookDelivery delivery, ResponseEntity<String> response,
            Duration requestDuration) {
        delivery.setStatus("SUCCESS");
        delivery.setResponseData(response.getBody());
        if (requestDuration != null) {
            log.info("Webhook delivery successful - ID: {}, Duration: {}ms",
                    delivery.getId(), requestDuration.toMillis());
        }
    }

    private void handleFailedDelivery(WebhookDelivery delivery, String error, Duration requestDuration) {
        delivery.setRetryCount(delivery.getRetryCount() + 1);
        delivery.setErrorMessage(error);

        if (delivery.getRetryCount() >= MAX_RETRIES) {
            delivery.setStatus("FAILED");
            log.error("Webhook delivery failed after {} retries - ID: {}, Error: {}",
                    MAX_RETRIES, delivery.getId(), error);
        } else {
            delivery.setStatus("PENDING_RETRY");
            long delaySeconds = RETRY_DELAYS[delivery.getRetryCount() - 1];
            LocalDateTime nextAttempt = LocalDateTime.now().plusSeconds(delaySeconds);

            log.info("Scheduling retry {} of {} for webhook ID: {} at {}",
                    delivery.getRetryCount(), MAX_RETRIES, delivery.getId(), nextAttempt);

            try {
                TimeUnit.SECONDS.sleep(delaySeconds);
                sendWebhookAsync(delivery);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.error("Retry scheduling interrupted for webhook ID: {}", delivery.getId());
            }
        }
    }

    private String generateSignature(String payload, String secret) {
        try {
            javax.crypto.Mac sha256_HMAC = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(
                    secret.getBytes(), "HmacSHA256");
            sha256_HMAC.init(secretKey);
            byte[] hash = sha256_HMAC.doFinal(payload.getBytes());
            return java.util.Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.error("Error generating webhook signature", e);
            return "";
        }
    }

    private void updateConfigurationFromDTO(WebhookConfiguration config, WebhookConfigurationDTO dto) {
        config.setName(dto.getName());
        config.setProviderType(dto.getProviderType());
        config.setEndpoint(dto.getEndpoint());
        config.setActive(dto.isActive());
        config.setSubscribedEvents(dto.getSubscribedEvents());
        config.setProviderSpecificConfig(dto.getProviderSpecificConfig());

        if (dto.getMaxRetries() > 0) {
            config.setMaxRetries(dto.getMaxRetries());
        }

        if (dto.getRetryDelay() > 0) {
            config.setRetryDelay(dto.getRetryDelay());
        }
    }

    private WebhookDeliveryDTO convertToDeliveryDTO(WebhookDelivery delivery) {
        return WebhookDeliveryDTO.builder()
                .id(delivery.getId())
                .webhookConfiguration(convertToDTO(delivery.getWebhook())) // Convert the full webhook configuration
                .eventType(delivery.getEventType())
                .payload(delivery.getPayload())
                .status(delivery.getStatus())
                .retryCount(delivery.getRetryCount())
                .createdAt(delivery.getCreatedAt())
                .lastAttempt(delivery.getLastAttempt())
                .responseData(delivery.getResponseData())
                .errorMessage(delivery.getErrorMessage())
                .build();
    }

    private WebhookConfigurationDTO convertToDTO(WebhookConfiguration config) {
        if (config == null) {
            return null;
        }
        return WebhookConfigurationDTO.builder()
                .id(config.getId())
                .name(config.getName())
                .providerType(config.getProviderType())
                .endpoint(config.getEndpoint())
                .active(config.isActive())
                .subscribedEvents(config.getSubscribedEvents())
                .providerSpecificConfig(config.getProviderSpecificConfig())
                .maxRetries(config.getMaxRetries())
                .retryDelay(config.getRetryDelay())
                .build();
    }
}