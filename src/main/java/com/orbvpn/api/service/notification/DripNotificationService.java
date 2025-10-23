package com.orbvpn.api.service.notification;

import com.orbvpn.api.domain.entity.NotificationSettings;
import com.orbvpn.api.domain.entity.UserProfile;
import com.orbvpn.api.repository.NotificationSettingsRepository;
import com.orbvpn.api.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
@RequiredArgsConstructor
public class DripNotificationService {

    private final UserProfileRepository userProfileRepository;
    private final TelegramService telegramService;
    private final WhatsAppService whatsAppService;
    private final NotificationSettingsRepository notificationSettingsRepository;

    private volatile boolean isCampaignRunning = false;
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private final AtomicInteger currentPage = new AtomicInteger(0);

    public void startDripCampaign(String channel, String message) {
        if (isCampaignRunning) {
            throw new IllegalStateException("A campaign is already running");
        }
        isCampaignRunning = true;
        log.info("Starting drip campaign on channel: {}", channel);

        NotificationSettings settings = notificationSettingsRepository.getSettings();
        int batchSize = settings.getBatchSize();
        int delay = settings.getDelayBetweenBatches();

        executorService.scheduleWithFixedDelay(() -> {
            if (!isCampaignRunning) {
                executorService.shutdown();
                return;
            }

            Pageable pageable = PageRequest.of(currentPage.getAndIncrement(), batchSize);
            List<UserProfile> batch = userProfileRepository.findNextBatch(pageable).getContent();

            if (batch.isEmpty()) {
                log.info("All users have been notified.");
                isCampaignRunning = false;
                executorService.shutdown();
                return;
            }

            for (UserProfile userProfile : batch) {
                try {
                    if ("telegram".equalsIgnoreCase(channel) && userProfile.getTelegramChatId() != null) {
                        telegramService.sendMessage(userProfile.getTelegramChatId(), message);
                    } else if ("whatsapp".equalsIgnoreCase(channel) && userProfile.getPhone() != null) {
                        whatsAppService.sendMessage(userProfile.getPhone(), message);
                    }
                } catch (Exception e) {
                    log.error("Failed to send message to user: {}", userProfile.getUser().getId(), e);
                }
            }

        }, 0, delay, TimeUnit.SECONDS);
    }

    public void stopDripCampaign() {
        isCampaignRunning = false;
        currentPage.set(0);
        log.info("Drip campaign stopped.");
    }

    public boolean isCampaignRunning() {
        return isCampaignRunning;
    }
}