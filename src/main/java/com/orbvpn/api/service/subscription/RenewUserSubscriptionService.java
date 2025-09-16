package com.orbvpn.api.service.subscription;

import com.orbvpn.api.domain.dto.UserSubscriptionView;
import com.orbvpn.api.domain.dto.AppleNotification;
import com.orbvpn.api.domain.dto.GoogleNotification;
import com.orbvpn.api.domain.dto.StripeWebhookEvent;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.domain.entity.Group;
import com.orbvpn.api.domain.entity.Payment;
import com.orbvpn.api.domain.entity.TrialHistory;
import com.orbvpn.api.domain.enums.GatewayName;
import com.orbvpn.api.service.payment.PaymentService;
import com.orbvpn.api.service.subscription.handlers.SubscriptionHandlerFactory;
import com.orbvpn.api.service.subscription.notification.StripeNotificationProcessor;
import com.orbvpn.api.service.subscription.notification.AppleNotificationProcessor;
import com.orbvpn.api.service.subscription.notification.GooglePlayNotificationProcessor;
import com.orbvpn.api.service.subscription.utils.*;
import com.orbvpn.api.service.notification.NotificationService;
import com.orbvpn.api.exception.SubscriptionException;
import com.orbvpn.api.repository.TrialHistoryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RenewUserSubscriptionService {
    private final SubscriptionHandlerFactory subscriptionHandlerFactory;
    private final TokenValidator tokenValidator;
    private final TransactionMappingService transactionMappingService;
    private final GooglePlayNotificationProcessor googlePlayNotificationProcessor;
    private final AppleNotificationProcessor appleNotificationProcessor;
    private final StripeNotificationProcessor stripeNotificationProcessor;
    private final SubscriptionStateManager subscriptionStateManager;
    private final NotificationService notificationService;
    private final PaymentService paymentService;
    private final TrialHistoryRepository trialHistoryRepository;

    @Transactional
    public void renewSubscriptions() {
        log.info("Starting subscription renewal process");
        try {
            List<Payment> paymentsToRenew = paymentService.findAllSubscriptionPaymentsToRenew();

            for (Payment payment : paymentsToRenew) {
                try {
                    log.debug("Attempting to renew payment ID: {}", payment.getId());
                    Payment renewedPayment = paymentService.renewPayment(payment);

                    if (renewedPayment != null) {
                        log.info("Successfully renewed payment ID: {}", payment.getId());

                        // Get the user and their current subscription
                        User user = renewedPayment.getUser();
                        UserSubscription currentSubscription = subscriptionStateManager.getCurrentSubscription(user);

                        // Send renewal notification
                        try {
                            if (currentSubscription != null) {
                                notificationService.sendSubscriptionRenewalNotification(user, currentSubscription);
                                log.debug("Sent renewal notification to user: {}", user.getEmail());
                            }
                        } catch (Exception e) {
                            log.error("Failed to send renewal notification to user: {} - Error: {}",
                                    user.getEmail(), e.getMessage(), e);
                            // Don't throw the exception here to allow the renewal process to continue
                        }
                    } else {
                        log.warn("Failed to renew payment ID: {}", payment.getId());
                    }
                } catch (Exception e) {
                    log.error("Error renewing payment ID: {}", payment.getId(), e);
                }
            }

            log.info("Completed subscription renewal process");
        } catch (Exception e) {
            log.error("Error during subscription renewal process", e);
            throw new RuntimeException("Failed to process subscription renewals", e);
        }
    }

    @Transactional
    public void assignSubscription(User user, int groupId, LocalDateTime expiresAt,
            GatewayName gateway, boolean renew, String token,
            Boolean isTrialPeriod, String subscriptionId) {

        log.info("Assigning subscription - User: {}, Group: {}, Gateway: {}, IsTrialPeriod: {}",
                user.getId(), groupId, gateway, isTrialPeriod);

        try {
            validateRequest(user, groupId, expiresAt, token);

            // First ensure mapping exists to avoid duplicate errors later
            transactionMappingService.ensureMapping(user, token, gateway);

            // Then validate token
            tokenValidator.validateToken(token, gateway, user);

            // Process subscription
            var handler = subscriptionHandlerFactory.getHandler(gateway);
            handler.handleSubscription(user, groupId, expiresAt, token, isTrialPeriod, subscriptionId);

            log.info("Successfully assigned subscription for user: {}", user.getId());
        } catch (Exception e) {
            log.error("Error assigning subscription: {}", e.getMessage(), e);
            throw new SubscriptionException("Failed to assign subscription: " + e.getMessage(), e);
        }

        if (Boolean.TRUE.equals(isTrialPeriod)) {
            try {
                // Record trial history
                TrialHistory trialRecord = TrialHistory.builder()
                        .userId(user.getId())
                        .deviceId(null) // Set this if you have device tracking
                        .platform(gateway.name())
                        .trialStartDate(LocalDateTime.now())
                        .trialEndDate(expiresAt)
                        .subscriptionId(subscriptionId != null ? subscriptionId : "unknown")
                        .transactionId(token)
                        .gateway(gateway)
                        .completed(false)
                        .build();

                trialHistoryRepository.save(trialRecord);
                log.info("Recorded trial subscription history for user: {}", user.getId());
            } catch (Exception ex) {
                // Log error but don't fail the subscription process
                log.error("Failed to record trial history for user {}: {}", user.getId(), ex.getMessage(), ex);
            }
        }
    }

    public void handleGooglePlayNotification(GoogleNotification notification) {
        googlePlayNotificationProcessor.processNotification(notification);
    }

    public void handleAppleNotification(AppleNotification notification) {
        appleNotificationProcessor.processNotification(notification);
    }

    public void handleStripeNotification(StripeWebhookEvent event) {
        stripeNotificationProcessor.processNotification(event);
    }

    public UserSubscriptionView renewWithDayCount(User user, Integer days) {
        UserSubscriptionView view = subscriptionStateManager.renewSubscription(user, days);
        try {
            UserSubscription subscription = subscriptionStateManager.getCurrentSubscription(user);
            if (subscription != null) {
                notificationService.sendSubscriptionRenewalNotification(user, subscription);
            }
        } catch (Exception e) {
            log.error("Failed to send renewal notification for user: {}", user.getEmail(), e);
        }
        return view;
    }

    public UserSubscriptionView resetUserSubscription(User user) {
        return subscriptionStateManager.resetSubscription(user);
    }

    public UserSubscriptionView resetUserSubscription(User user, int groupId) {
        return subscriptionStateManager.resetSubscription(user, groupId);
    }

    public UserSubscriptionView renewUserSubscription(User user) {
        UserSubscriptionView view = subscriptionStateManager.renewSubscription(user);
        try {
            UserSubscription subscription = subscriptionStateManager.getCurrentSubscription(user);
            if (subscription != null) {
                notificationService.sendSubscriptionRenewalNotification(user, subscription);
            }
        } catch (Exception e) {
            log.error("Failed to send renewal notification for user: {}", user.getEmail(), e);
        }
        return view;
    }

    public UserSubscriptionView renewUserSubscription(User user, Group group) {
        UserSubscriptionView view = subscriptionStateManager.renewSubscription(user, group);
        try {
            UserSubscription subscription = subscriptionStateManager.getCurrentSubscription(user);
            if (subscription != null) {
                notificationService.sendSubscriptionRenewalNotification(user, subscription);
            }
        } catch (Exception e) {
            log.error("Failed to send renewal notification for user: {}", user.getEmail(), e);
        }
        return view;
    }

    public UserSubscriptionView resellerResetUserSubscription(User user) {
        return subscriptionStateManager.resellerResetSubscription(user);
    }

    public UserSubscriptionView resellerResetUserSubscription(User user, Group group) {
        return subscriptionStateManager.resellerResetSubscription(user, group);
    }

    public UserSubscriptionView resellerRenewUserSubscription(User user) {
        UserSubscriptionView view = subscriptionStateManager.resellerRenewSubscription(user);

        try {
            UserSubscription subscription = subscriptionStateManager.getCurrentSubscription(user);
            if (subscription != null) {
                notificationService.sendSubscriptionRenewalNotification(user, subscription);
            }
        } catch (Exception e) {
            log.error("Failed to send renewal notification for user: {}", user.getEmail(), e);
        }

        return view;
    }

    public UserSubscriptionView resellerRenewUserSubscription(User user, Group group) {
        UserSubscriptionView view = subscriptionStateManager.resellerRenewSubscription(user, group);

        try {
            UserSubscription subscription = subscriptionStateManager.getCurrentSubscription(user);
            if (subscription != null) {
                notificationService.sendSubscriptionRenewalNotification(user, subscription);
            }
        } catch (Exception e) {
            log.error("Failed to send renewal notification for user: {}", user.getEmail(), e);
        }

        return view;
    }

    private void validateRequest(User user, int groupId, LocalDateTime expiresAt, String token) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        if (groupId <= 0) {
            throw new IllegalArgumentException("Invalid group ID: " + groupId);
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("Expiration date cannot be null");
        }
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid token");
        }

        log.debug("Request validation successful - User: {}, GroupId: {}, ExpiresAt: {}",
                user.getId(), groupId, expiresAt);
    }

}