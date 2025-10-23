package com.orbvpn.api.service.webhook;

import com.orbvpn.api.domain.entity.WebhookConfiguration;
import com.orbvpn.api.domain.enums.WebhookProviderType;
import com.orbvpn.api.exception.WebhookException;

import java.util.Map;

public interface WebhookProvider {
    WebhookProviderType getProviderType();

    void sendWebhook(WebhookConfiguration config, String eventType, Map<String, Object> payload)
            throws WebhookException;
}