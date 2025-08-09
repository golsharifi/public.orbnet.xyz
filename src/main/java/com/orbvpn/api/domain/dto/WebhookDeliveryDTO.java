package com.orbvpn.api.domain.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookDeliveryDTO {
    private Long id;
    private WebhookConfigurationDTO webhookConfiguration; // Changed from webhookConfigurationId
    private String eventType;
    private String payload;
    private String status;
    private int retryCount;
    private LocalDateTime createdAt;
    private LocalDateTime lastAttempt;
    private String responseData;
    private String errorMessage;
}