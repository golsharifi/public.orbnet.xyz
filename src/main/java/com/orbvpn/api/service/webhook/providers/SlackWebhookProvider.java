package com.orbvpn.api.service.webhook.providers;

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
public class SlackWebhookProvider implements WebhookProvider {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public WebhookProviderType getProviderType() {
        return WebhookProviderType.SLACK;
    }

    @Override
    public void sendWebhook(WebhookConfiguration config, String eventType, Map<String, Object> payload)
            throws WebhookException {
        try {
            // Convert to Slack message format
            Map<String, Object> slackPayload = new HashMap<>();
            slackPayload.put("text", formatSlackMessage(eventType, payload));

            // Add blocks for better formatting if needed
            slackPayload.put("blocks", createSlackBlocks(eventType, payload));

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");

            HttpEntity<String> request = new HttpEntity<>(
                    objectMapper.writeValueAsString(slackPayload),
                    headers);

            restTemplate.postForEntity(config.getEndpoint(), request, String.class);
        } catch (Exception e) {
            throw new WebhookException("Failed to send Slack webhook", e);
        }
    }

    private String formatSlackMessage(String eventType, Map<String, Object> payload) {
        // Basic message formatting
        StringBuilder message = new StringBuilder();
        message.append("*Event:* ").append(eventType).append("\n");

        payload.forEach((key, value) -> message.append("*").append(key).append(":* ")
                .append(value.toString()).append("\n"));

        return message.toString();
    }

    private Object createSlackBlocks(String eventType, Map<String, Object> payload) {
        // Implementation for Slack Block Kit formatting
        // https://api.slack.com/block-kit
        return null;
    }
}