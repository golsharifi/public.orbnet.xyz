package com.orbvpn.api.listener;

import com.orbvpn.api.event.SubscriptionChangedEvent;
import com.orbvpn.api.service.notification.NotificationOperations;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionEventListener {
    private final NotificationOperations notificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleSubscriptionChanged(SubscriptionChangedEvent event) {
        try {
            log.debug("Handling subscription event of type: {}", event.getEventType());

            switch (event.getEventType()) {
                case "NEW_SUBSCRIPTION":
                    if (event.getPassword() != null) {
                        notificationService.welcomingNewUsersCreatedByAdmin(
                                event.getUser(),
                                event.getSubscription(),
                                event.getPassword());
                    } else {
                        // Handle subscription creation without password notification if needed
                    }
                    break;

                case "SUBSCRIPTION_EXPIRED":
                    // Handle expired subscription notification
                    break;

                case "SUBSCRIPTION_RENEWED":
                    // Handle renewal notification
                    break;

                case "SUBSCRIPTION_CANCELLED":
                    // Handle cancellation notification
                    break;

                default:
                    log.warn("Unknown subscription event type: {}", event.getEventType());
                    break;
            }
        } catch (Exception e) {
            log.error("Error handling subscription event: {}", event.getEventType(), e);
        }
    }
}