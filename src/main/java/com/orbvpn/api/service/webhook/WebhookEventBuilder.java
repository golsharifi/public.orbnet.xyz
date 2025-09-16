package com.orbvpn.api.service.webhook;

import com.orbvpn.api.domain.entity.WebhookEventPayload;
import com.orbvpn.api.domain.enums.WebhookEventType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class WebhookEventBuilder {
    @Value("${spring.profiles.active:unknown}")
    private String environment;

    @Value("${app.api.version:1.0}")
    private String apiVersion;

    public WebhookEventPayload buildPayload(WebhookEventType eventType, Object entity) {
        return buildPayload(eventType, entity, null);
    }

    public WebhookEventPayload buildPayload(WebhookEventType eventType, Object entity,
            Map<String, Object> additionalData) {
        Map<String, Object> data = new HashMap<>();

        // Add entity data
        if (entity != null) {
            data.put("entity", entity);
        }

        // Add additional data if provided
        if (additionalData != null) {
            data.putAll(additionalData);
        }

        // Add metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("processed_at", LocalDateTime.now().toString());
        metadata.put("event_version", "1.0");

        return WebhookEventPayload.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType.name())
                .eventName(eventType.getEventName())
                .timestamp(LocalDateTime.now())
                .data(data)
                .metadata(metadata)
                .environment(environment)
                .apiVersion(apiVersion)
                .build();
    }
}