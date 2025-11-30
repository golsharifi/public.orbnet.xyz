package com.orbvpn.api.domain.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebhookEventPayload {
    private String eventId;
    private String eventType;
    private String eventName;
    private LocalDateTime timestamp;
    private Map<String, Object> data;
    private Map<String, Object> metadata;
    private String environment;
    private String apiVersion;
}