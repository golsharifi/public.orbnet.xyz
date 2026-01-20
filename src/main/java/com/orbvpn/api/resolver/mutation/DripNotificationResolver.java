package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.entity.NotificationSettings;
import com.orbvpn.api.service.notification.DripNotificationService;
import com.orbvpn.api.repository.NotificationSettingsRepository;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

@Slf4j
@Controller
@RequiredArgsConstructor
public class DripNotificationResolver {
    private final DripNotificationService dripNotificationService;
    private final NotificationSettingsRepository notificationSettingsRepository;

    @Secured(ADMIN)
    @MutationMapping
    public Boolean startDripCampaign(
            @Argument @Valid @NotBlank(message = "Channel cannot be empty") String channel,
            @Argument @Valid @NotBlank(message = "Message cannot be empty") String message) {
        log.info("Starting drip campaign on channel: {}", channel);
        try {
            dripNotificationService.startDripCampaign(channel, message);
            log.info("Successfully started drip campaign on channel: {}", channel);
            return true;
        } catch (Exception e) {
            log.error("Error starting drip campaign - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public Boolean stopDripCampaign() {
        log.info("Stopping drip campaign");
        try {
            dripNotificationService.stopDripCampaign();
            log.info("Successfully stopped drip campaign");
            return true;
        } catch (Exception e) {
            log.error("Error stopping drip campaign - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public Boolean updateNotificationSettings(
            @Argument @Valid @Positive(message = "Batch size must be positive") Integer batchSize,
            @Argument @Valid @Positive(message = "Delay must be positive") Integer delayBetweenBatches) {
        log.info("Updating notification settings - batchSize: {}, delay: {}", batchSize, delayBetweenBatches);
        try {
            NotificationSettings settings = notificationSettingsRepository.getSettings();
            if (batchSize != null)
                settings.setBatchSize(batchSize);
            if (delayBetweenBatches != null)
                settings.setDelayBetweenBatches(delayBetweenBatches);
            notificationSettingsRepository.save(settings);
            log.info("Successfully updated notification settings");
            return true;
        } catch (Exception e) {
            log.error("Error updating notification settings - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @QueryMapping
    public Boolean isDripCampaignRunning() {
        log.info("Checking if drip campaign is running");
        try {
            Boolean isRunning = dripNotificationService.isCampaignRunning();
            log.info("Drip campaign running status: {}", isRunning);
            return isRunning;
        } catch (Exception e) {
            log.error("Error checking drip campaign status - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @QueryMapping
    public NotificationSettings getNotificationSettings() {
        log.info("Fetching notification settings");
        try {
            NotificationSettings settings = notificationSettingsRepository.getSettings();
            log.info("Successfully retrieved notification settings");
            return settings;
        } catch (Exception e) {
            log.error("Error fetching notification settings - Error: {}", e.getMessage(), e);
            throw e;
        }
    }
}