package com.orbvpn.api.domain.dto;

import com.orbvpn.api.domain.enums.WebhookProviderType;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookConfigurationDTO {
    private Long id;
    private String name;
    private WebhookProviderType providerType;
    private String endpoint;
    private String secret;
    private boolean active;
    private Set<String> subscribedEvents;
    private String providerSpecificConfig;
    private int maxRetries;
    private int retryDelay;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}