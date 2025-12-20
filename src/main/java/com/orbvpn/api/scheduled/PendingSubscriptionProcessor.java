package com.orbvpn.api.scheduled;

import com.orbvpn.api.domain.entity.PendingSubscription;
import com.orbvpn.api.domain.dto.GoogleNotification;
import com.orbvpn.api.domain.dto.AppleNotification;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.repository.PendingSubscriptionRepository;
import com.orbvpn.api.service.subscription.notification.GooglePlayNotificationProcessor;
import com.orbvpn.api.service.subscription.notification.AppleNotificationProcessor;
import com.orbvpn.api.service.subscription.utils.TransactionMappingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PendingSubscriptionProcessor {
    private final PendingSubscriptionRepository pendingSubscriptionRepository;
    private final TransactionMappingService transactionMappingService;
    private final GooglePlayNotificationProcessor googlePlayNotificationProcessor;
    private final AppleNotificationProcessor appleNotificationProcessor;

    @Scheduled(fixedDelay = 300000) // Run every 5 minutes
    @Transactional
    public void processPendingSubscriptions() {
        try {
            List<PendingSubscription> pendingSubscriptions = pendingSubscriptionRepository.findAll()
                    .stream()
                    .filter(ps -> ps.getProcessedAt() == null)
                    .toList();

            for (PendingSubscription pending : pendingSubscriptions) {
                try {
                    processPendingSubscription(pending);
                } catch (Exception e) {
                    log.error("Error processing pending subscription: {}", pending.getSubscriptionId(), e);
                }
            }
        } catch (Exception e) {
            log.error("Error in pending subscription processor", e);
        }
    }

    private void processPendingSubscription(PendingSubscription pending) {
        // Check if user mapping exists now
        User user = transactionMappingService.findUserByToken(
                pending.getSubscriptionId(),
                pending.getGateway());

        if (user != null) {
            try {
                switch (pending.getGateway()) {
                    case GOOGLE_PLAY -> processGooglePlaySubscription(pending);
                    case APPLE_STORE -> processAppleSubscription(pending);
                    default -> log.warn("Unsupported gateway: {}", pending.getGateway());
                }

                // Mark as processed
                pending.setProcessedAt(LocalDateTime.now());
                pendingSubscriptionRepository.save(pending);

                log.info("Successfully processed pending subscription: {} for gateway: {}",
                        pending.getSubscriptionId(), pending.getGateway());
            } catch (Exception e) {
                log.error("Failed to process pending subscription: {}", pending.getSubscriptionId(), e);
            }
        }
    }

    @Scheduled(fixedDelay = 300000) // Run every 5 minutes
    @Transactional
    public void cleanupProcessedSubscriptions() {
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusHours(24);
            pendingSubscriptionRepository.deleteByProcessedAtBefore(cutoffTime);
        } catch (Exception e) {
            log.error("Error cleaning up processed subscriptions", e);
        }
    }

    private void processGooglePlaySubscription(PendingSubscription pending) {
        GoogleNotification notification = new GoogleNotification();
        GoogleNotification.SubscriptionNotification subNotification = new GoogleNotification.SubscriptionNotification();

        subNotification.setPurchaseToken(pending.getSubscriptionId());
        subNotification.setSubscriptionId(pending.getSubscriptionId());
        subNotification.setNotificationType(4); // SUBSCRIPTION_PURCHASED

        notification.setSubscriptionNotification(subNotification);

        // Generate a unique message ID for internal calls
        String messageId = "pending-" + pending.getId() + "-" + System.currentTimeMillis();
        googlePlayNotificationProcessor.processNotification(notification, messageId);
    }

    private void processAppleSubscription(PendingSubscription pending) {
        AppleNotification notification = new AppleNotification();
        notification.setNotificationType("SUBSCRIPTION_PURCHASED");

        AppleNotification.Data data = new AppleNotification.Data();
        data.setSignedTransactionInfo(pending.getSubscriptionId());
        notification.setData(data);

        appleNotificationProcessor.processNotification(notification);
    }
}
