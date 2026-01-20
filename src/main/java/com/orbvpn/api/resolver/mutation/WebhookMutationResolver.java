package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.WebhookConfigurationDTO;
import com.orbvpn.api.service.webhook.WebhookService;
import com.orbvpn.api.exception.NotFoundException;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebhookMutationResolver {
    private final WebhookService webhookService;

    @Secured(ADMIN)
    @MutationMapping
    public WebhookConfigurationDTO createWebhookConfiguration(
            @Argument @Valid @NotNull(message = "Webhook configuration is required") WebhookConfigurationDTO input) {
        log.info("Creating webhook configuration");
        try {
            WebhookConfigurationDTO config = webhookService.createConfiguration(input);
            log.info("Successfully created webhook configuration: {}", config.getId());
            return config;
        } catch (Exception e) {
            log.error("Error creating webhook configuration - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public WebhookConfigurationDTO updateWebhookConfiguration(
            @Argument @Valid @Positive(message = "ID must be positive") Long id,
            @Argument @Valid @NotNull(message = "Webhook configuration is required") WebhookConfigurationDTO input) {
        log.info("Updating webhook configuration: {}", id);
        try {
            WebhookConfigurationDTO config = webhookService.updateConfiguration(id, input);
            if (config == null) {
                throw new NotFoundException("Webhook configuration not found with id: " + id);
            }
            log.info("Successfully updated webhook configuration: {}", id);
            return config;
        } catch (NotFoundException e) {
            log.warn("Webhook configuration not found - ID: {} - Error: {}", id, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error updating webhook configuration: {} - Error: {}", id, e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public Boolean deleteWebhookConfiguration(
            @Argument @Valid @Positive(message = "ID must be positive") Long id) {
        log.info("Deleting webhook configuration: {}", id);
        try {
            Boolean result = webhookService.deleteConfiguration(id);
            log.info("Successfully deleted webhook configuration: {}", id);
            return result;
        } catch (Exception e) {
            log.error("Error deleting webhook configuration: {} - Error: {}", id, e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public Boolean testWebhookConfiguration(
            @Argument @Valid @Positive(message = "ID must be positive") Long id) {
        log.info("Testing webhook configuration: {}", id);
        try {
            Boolean result = webhookService.testConfiguration(id);
            log.info("Successfully tested webhook configuration: {}", id);
            return result;
        } catch (Exception e) {
            log.error("Error testing webhook configuration: {} - Error: {}", id, e.getMessage(), e);
            throw e;
        }
    }
}