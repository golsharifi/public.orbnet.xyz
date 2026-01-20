package com.orbvpn.api.service.subscription.handlers;

import com.orbvpn.api.domain.entity.Group;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.domain.enums.GatewayName;
import com.orbvpn.api.domain.enums.SubscriptionStatus;
import com.orbvpn.api.exception.SubscriptionException;
import com.orbvpn.api.repository.UserSubscriptionRepository;
import com.orbvpn.api.service.AsyncNotificationHelper;
import com.orbvpn.api.service.GroupService;
import com.orbvpn.api.service.RadiusService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppleSubscriptionHandler implements SubscriptionHandler {
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final GroupService groupService;
    private final RadiusService radiusService;
    private final AsyncNotificationHelper asyncNotificationHelper;

    @Override
    @Transactional
    public void handleSubscription(User user, int groupId, LocalDateTime expiresAt,
            String token, Boolean isTrialPeriod, String subscriptionId) {

        log.info("Processing Apple subscription for user: {} with group: {}, Trial: {}",
                user.getId(), groupId, isTrialPeriod);

        try {
            // Delete any existing subscriptions
            userSubscriptionRepository.deleteByUserId(user.getId());
            userSubscriptionRepository.flush();

            Group group = groupService.getById(groupId);
            if (group == null) {
                throw new SubscriptionException("Group not found: " + groupId);
            }

            UserSubscription subscription = new UserSubscription();
            subscription.setUser(user);
            subscription.setGroup(group);
            subscription.setExpiresAt(expiresAt);
            subscription.setOriginalTransactionId(token);
            subscription.setSubscriptionId(subscriptionId);
            subscription.setIsTrialPeriod(isTrialPeriod);

            // Set trial end date if it's a trial
            if (Boolean.TRUE.equals(isTrialPeriod)) {
                subscription.setTrialEndDate(expiresAt);
            }

            subscription.setDuration(group.getDuration());
            subscription.setMultiLoginCount(group.getMultiLoginCount());
            subscription.setDailyBandwidth(group.getDailyBandwidth());
            subscription.setDownloadUpload(group.getDownloadUpload());
            subscription.setGateway(GatewayName.APPLE_STORE);
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscription.setAutoRenew(true);
            subscription.setCanceled(false);

            UserSubscription savedSubscription = userSubscriptionRepository.save(subscription);
            radiusService.createUserRadChecks(savedSubscription);

            asyncNotificationHelper.sendSubscriptionWebhookAsync(savedSubscription, "SUBSCRIPTION_CREATED");

        } catch (Exception e) {
            log.error("Error processing Apple subscription for user: {} - Error: {}",
                    user.getId(), e.getMessage(), e);
            throw new SubscriptionException("Failed to process Apple subscription: " + e.getMessage(), e);
        }
    }

    @Override
    public GatewayName getGatewayType() {
        return GatewayName.APPLE_STORE;
    }
}