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

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ZapierWebhookProvider implements WebhookProvider {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public WebhookProviderType getProviderType() {
        return WebhookProviderType.ZAPIER;
    }

    @Override
    public void sendWebhook(WebhookConfiguration config, String eventType, Map<String, Object> payload)
            throws WebhookException {
        try {
            // Transform to Zapier-friendly format
            Map<String, Object> zapierPayload = createZapierPayload(eventType, payload);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");

            // Add hook ID if specified in config
            String hookId = getHookId(config);
            if (hookId != null) {
                headers.set("X-Hook-Id", hookId);
            }

            HttpEntity<String> request = new HttpEntity<>(
                    objectMapper.writeValueAsString(zapierPayload),
                    headers);

            restTemplate.postForEntity(config.getEndpoint(), request, String.class);
        } catch (Exception e) {
            throw new WebhookException("Failed to send Zapier webhook", e);
        }
    }

    private Map<String, Object> createZapierPayload(String eventType, Map<String, Object> payload) {
        Map<String, Object> zapierPayload = new HashMap<>();

        // Add event metadata
        zapierPayload.put("event_type", eventType);
        zapierPayload.put("timestamp", LocalDateTime.now().toEpochSecond(ZoneOffset.UTC));
        zapierPayload.put("source", "orbvpn");

        // Flatten nested objects for Zapier
        Map<String, Object> flatData = new HashMap<>();
        flattenMap("", payload, flatData);

        zapierPayload.put("data", flatData);
        return zapierPayload;
    }

    private void flattenMap(String prefix, Map<String, Object> map, Map<String, Object> flatMap) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "_" + entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) value;
                flattenMap(key, nestedMap, flatMap);
            } else {
                flatMap.put(key, value != null ? value.toString() : null);
            }
        }
    }

    private String getHookId(WebhookConfiguration config) {
        try {
            TypeReference<Map<String, String>> typeRef = new TypeReference<Map<String, String>>() {
            };
            Map<String, String> providerConfig = objectMapper.readValue(
                    config.getProviderSpecificConfig(),
                    typeRef);
            return providerConfig.get("hookId");
        } catch (Exception e) {
            log.error("Error parsing Zapier provider config", e);
            return null;
        }
    }
}