package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.dto.WebhookConfigurationDTO;
import com.orbvpn.api.domain.dto.WebhookDeliveryDTO;
import com.orbvpn.api.service.webhook.WebhookService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.springframework.security.access.annotation.Secured;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.List;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebhookQueryResolver {
    private final WebhookService webhookService;

    @Secured(ADMIN)
    @QueryMapping
    public List<WebhookConfigurationDTO> getWebhookConfigurations() {
        log.info("Fetching all webhook configurations");
        try {
            List<WebhookConfigurationDTO> configs = webhookService.getAllConfigurations();
            log.info("Successfully retrieved {} webhook configurations", configs.size());
            return configs;
        } catch (Exception e) {
            log.error("Error fetching webhook configurations - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @QueryMapping
    public WebhookConfigurationDTO getWebhookConfiguration(@Argument Long id) {
        log.info("Fetching webhook configuration with id: {}", id);
        try {
            WebhookConfigurationDTO config = webhookService.getConfiguration(id);
            log.info("Successfully retrieved webhook configuration: {}", id);
            return config;
        } catch (Exception e) {
            log.error("Error fetching webhook configuration: {} - Error: {}", id, e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @QueryMapping
    public List<WebhookDeliveryDTO> getWebhookDeliveries(
            @Argument Long configId,
            @Argument int page,
            @Argument int size) {
        log.info("Fetching webhook deliveries for config: {}, page: {}, size: {}", configId, page, size);
        try {
            List<WebhookDeliveryDTO> deliveries = webhookService.getDeliveries(configId, page, size);
            log.info("Successfully retrieved {} webhook deliveries for config: {}", deliveries.size(), configId);
            return deliveries;
        } catch (Exception e) {
            log.error("Error fetching webhook deliveries for config: {} - Error: {}", configId, e.getMessage(), e);
            throw e;
        }
    }
}