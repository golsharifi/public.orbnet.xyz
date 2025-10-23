package com.orbvpn.api.service.webhook.providers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbvpn.api.domain.entity.WebhookConfiguration;
import com.orbvpn.api.domain.enums.WebhookProviderType;
import com.orbvpn.api.exception.WebhookException;
import com.orbvpn.api.service.webhook.WebhookProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GoHighLevelWebhookProvider implements WebhookProvider {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public WebhookProviderType getProviderType() {
        return WebhookProviderType.GOHIGHLEVEL;
    }

    @Override
    public void sendWebhook(WebhookConfiguration config, String eventType, Map<String, Object> payload)
            throws WebhookException {
        try {
            // Transform payload to GoHighLevel format
            Map<String, Object> ghlPayload = createGHLPayload(eventType, payload);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            headers.set("Authorization", getAuthorizationHeader(config));

            HttpEntity<String> request = new HttpEntity<>(
                    objectMapper.writeValueAsString(ghlPayload),
                    headers);

            // Send to GoHighLevel
            restTemplate.postForEntity(config.getEndpoint(), request, String.class);
        } catch (Exception e) {
            throw new WebhookException("Failed to send GoHighLevel webhook", e);
        }
    }

    private Map<String, Object> createGHLPayload(String eventType, Map<String, Object> payload) {
        Map<String, Object> ghlPayload = new HashMap<>();

        // Add event information
        ghlPayload.put("event", eventType);
        ghlPayload.put("timestamp", System.currentTimeMillis());

        // Handle different event types
        switch (eventType) {
            case "USER_CREATED":
            case "USER_UPDATED":
                addContactData(ghlPayload, payload);
                break;
            case "SUBSCRIPTION_CREATED":
            case "SUBSCRIPTION_RENEWED":
                addOpportunityData(ghlPayload, payload);
                break;
            case "PAYMENT_SUCCEEDED":
                addPaymentData(ghlPayload, payload);
                break;
            default:
                ghlPayload.put("data", payload);
        }

        return ghlPayload;
    }

    private void addContactData(Map<String, Object> ghlPayload, Map<String, Object> payload) {
        Map<String, Object> contact = new HashMap<>();

        // Map user data to GHL contact format
        if (payload.containsKey("username")) {
            contact.put("name", payload.get("username"));
        }
        if (payload.containsKey("email")) {
            contact.put("email", payload.get("email"));
        }

        // Add custom fields
        Map<String, Object> customFields = new HashMap<>();
        customFields.put("userId", payload.get("userId"));
        customFields.put("accountType", "VPN User");
        contact.put("customFields", customFields);

        ghlPayload.put("contact", contact);
    }

    private void addOpportunityData(Map<String, Object> ghlPayload, Map<String, Object> payload) {
        Map<String, Object> opportunity = new HashMap<>();

        // Map subscription data to GHL opportunity format
        opportunity.put("name", "VPN Subscription");
        opportunity.put("value", payload.getOrDefault("amount", 0));
        opportunity.put("status", "won");

        ghlPayload.put("opportunity", opportunity);
    }

    private void addPaymentData(Map<String, Object> ghlPayload, Map<String, Object> payload) {
        Map<String, Object> payment = new HashMap<>();

        // Map payment data to GHL format
        payment.put("amount", payload.getOrDefault("amount", 0));
        payment.put("currency", payload.getOrDefault("currency", "USD"));
        payment.put("status", "completed");

        ghlPayload.put("payment", payment);
    }

    private String getAuthorizationHeader(WebhookConfiguration config) {
        try {
            TypeReference<Map<String, String>> typeRef = new TypeReference<Map<String, String>>() {
            };
            Map<String, String> providerConfig = objectMapper.readValue(
                    config.getProviderSpecificConfig(),
                    typeRef);

            String apiKey = providerConfig.get("apiKey");
            if (apiKey == null || apiKey.isEmpty()) {
                log.error("API key not found in GoHighLevel provider config");
                return "";
            }

            return "Bearer " + apiKey;
        } catch (Exception e) {
            log.error("Error parsing GoHighLevel provider config: {}", e.getMessage());
            return "";
        }
    }
}