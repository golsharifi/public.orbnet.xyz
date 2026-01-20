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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Slack webhook provider with Block Kit formatting support.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlackWebhookProvider implements WebhookProvider {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final Map<String, String> EVENT_EMOJIS = Map.of(
            "USER_CREATED", ":wave:",
            "USER_DELETED", ":x:",
            "SUBSCRIPTION_CREATED", ":tada:",
            "SUBSCRIPTION_RENEWED", ":recycle:",
            "SUBSCRIPTION_EXPIRED", ":warning:",
            "PAYMENT_SUCCEEDED", ":moneybag:",
            "PAYMENT_FAILED", ":no_entry:",
            "SYSTEM_ERROR", ":rotating_light:"
    );

    @Override
    public WebhookProviderType getProviderType() {
        return WebhookProviderType.SLACK;
    }

    @Override
    public void sendWebhook(WebhookConfiguration config, String eventType, Map<String, Object> payload)
            throws WebhookException {
        try {
            Map<String, Object> slackPayload = new HashMap<>();
            slackPayload.put("text", formatSlackMessage(eventType, payload));
            slackPayload.put("blocks", createSlackBlocks(eventType, payload));

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");

            HttpEntity<String> request = new HttpEntity<>(
                    objectMapper.writeValueAsString(slackPayload),
                    headers);

            restTemplate.postForEntity(config.getEndpoint(), request, String.class);
            log.debug("Slack webhook sent successfully for event: {}", eventType);
        } catch (Exception e) {
            log.error("Failed to send Slack webhook for event {}: {}", eventType, e.getMessage());
            throw new WebhookException("Failed to send Slack webhook", e);
        }
    }

    private String formatSlackMessage(String eventType, Map<String, Object> payload) {
        String emoji = EVENT_EMOJIS.getOrDefault(eventType, ":bell:");
        StringBuilder message = new StringBuilder();
        message.append(emoji).append(" *").append(formatEventType(eventType)).append("*\n");
        return message.toString();
    }

    private List<Map<String, Object>> createSlackBlocks(String eventType, Map<String, Object> payload) {
        List<Map<String, Object>> blocks = new ArrayList<>();

        // Header block
        String emoji = EVENT_EMOJIS.getOrDefault(eventType, ":bell:");
        blocks.add(createHeaderBlock(emoji + " " + formatEventType(eventType)));

        // Divider
        blocks.add(Map.of("type", "divider"));

        // Context block with timestamp
        blocks.add(createContextBlock("Event received at: " +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));

        // Extract and display user information if present
        if (payload.containsKey("user") && payload.get("user") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> user = (Map<String, Object>) payload.get("user");
            blocks.add(createSectionBlock("*User Information*"));
            blocks.add(createFieldsBlock(extractUserFields(user)));
        }

        // Extract and display subscription information if present
        if (payload.containsKey("subscription") && payload.get("subscription") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> subscription = (Map<String, Object>) payload.get("subscription");
            blocks.add(createSectionBlock("*Subscription Details*"));
            blocks.add(createFieldsBlock(extractSubscriptionFields(subscription)));
        }

        // Extract and display payment information if present
        if (payload.containsKey("payment") && payload.get("payment") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> payment = (Map<String, Object>) payload.get("payment");
            blocks.add(createSectionBlock("*Payment Details*"));
            blocks.add(createFieldsBlock(extractPaymentFields(payment)));
        }

        // Display other top-level fields
        List<Map<String, String>> otherFields = extractOtherFields(payload);
        if (!otherFields.isEmpty()) {
            blocks.add(createSectionBlock("*Additional Information*"));
            blocks.add(createFieldsBlock(otherFields));
        }

        return blocks;
    }

    private Map<String, Object> createHeaderBlock(String text) {
        return Map.of(
                "type", "header",
                "text", Map.of("type", "plain_text", "text", text, "emoji", true)
        );
    }

    private Map<String, Object> createContextBlock(String text) {
        return Map.of(
                "type", "context",
                "elements", List.of(Map.of("type", "mrkdwn", "text", text))
        );
    }

    private Map<String, Object> createSectionBlock(String text) {
        return Map.of(
                "type", "section",
                "text", Map.of("type", "mrkdwn", "text", text)
        );
    }

    private Map<String, Object> createFieldsBlock(List<Map<String, String>> fields) {
        List<Map<String, Object>> fieldElements = new ArrayList<>();
        for (Map<String, String> field : fields) {
            fieldElements.add(Map.of(
                    "type", "mrkdwn",
                    "text", "*" + field.get("label") + "*\n" + field.get("value")
            ));
        }
        return Map.of("type", "section", "fields", fieldElements);
    }

    private List<Map<String, String>> extractUserFields(Map<String, Object> user) {
        List<Map<String, String>> fields = new ArrayList<>();
        addFieldIfPresent(fields, "Email", user.get("email"));
        addFieldIfPresent(fields, "Username", user.get("username"));
        addFieldIfPresent(fields, "User ID", user.get("id"));
        addFieldIfPresent(fields, "Status", user.get("status"));
        return fields;
    }

    private List<Map<String, String>> extractSubscriptionFields(Map<String, Object> subscription) {
        List<Map<String, String>> fields = new ArrayList<>();
        addFieldIfPresent(fields, "Plan", subscription.get("groupName"));
        addFieldIfPresent(fields, "Duration", subscription.get("duration"));
        addFieldIfPresent(fields, "Expiration", subscription.get("expirationDate"));
        addFieldIfPresent(fields, "Multi-Login", subscription.get("multiLoginCount"));
        return fields;
    }

    private List<Map<String, String>> extractPaymentFields(Map<String, Object> payment) {
        List<Map<String, String>> fields = new ArrayList<>();
        addFieldIfPresent(fields, "Amount", payment.get("amount"));
        addFieldIfPresent(fields, "Gateway", payment.get("gateway"));
        addFieldIfPresent(fields, "Status", payment.get("status"));
        addFieldIfPresent(fields, "Transaction ID", payment.get("transactionId"));
        return fields;
    }

    private List<Map<String, String>> extractOtherFields(Map<String, Object> payload) {
        List<Map<String, String>> fields = new ArrayList<>();
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (!(value instanceof Map) && !key.equals("eventId") && !key.equals("timestamp")) {
                addFieldIfPresent(fields, formatLabel(key), value);
            }
        }
        return fields;
    }

    private void addFieldIfPresent(List<Map<String, String>> fields, String label, Object value) {
        if (value != null && !value.toString().isEmpty()) {
            fields.add(Map.of("label", label, "value", value.toString()));
        }
    }

    private String formatEventType(String eventType) {
        return eventType.replace("_", " ");
    }

    private String formatLabel(String key) {
        String result = key.replaceAll("([a-z])([A-Z])", "$1 $2");
        return result.substring(0, 1).toUpperCase() + result.substring(1);
    }
}