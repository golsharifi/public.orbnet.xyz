package com.orbvpn.api.service.subscription.utils;

import com.orbvpn.api.domain.entity.Group;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.domain.enums.GatewayName;
import com.orbvpn.api.domain.enums.SubscriptionStatus;
import com.orbvpn.api.exception.ConcurrentSubscriptionException;
import com.orbvpn.api.exception.NotFoundException;
import com.orbvpn.api.mapper.UserSubscriptionViewMapper;
import com.orbvpn.api.repository.UserSubscriptionRepository;
import com.orbvpn.api.service.RadiusService;
import com.orbvpn.api.service.AsyncNotificationHelper;
import com.orbvpn.api.domain.dto.UserSubscriptionView;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionStateManager {
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final RadiusService radiusService;
    private final AsyncNotificationHelper asyncNotificationHelper;
    private final UserSubscriptionViewMapper subscriptionViewMapper;

    @Transactional
    public UserSubscriptionView renewSubscription(User user, Integer days) {
        log.info("Starting subscription renewal for User {} with {} days", user.getId(), days);

        UserSubscription subscription = getCurrentSubscription(user);
        if (subscription == null) {
            throw new NotFoundException("No active subscription found for user");
        }

        // If days is null, use the current group's duration
        int daysToAdd = days != null ? days : subscription.getGroup().getDuration();

        // Extend the subscription
        extendSubscription(subscription, daysToAdd);

        asyncNotificationHelper.sendSubscriptionWebhookAsync(subscription, "SUBSCRIPTION_RENEWED");

        return subscriptionViewMapper.toView(subscription);
    }

    @Transactional
    public UserSubscriptionView resetSubscription(User user) {
        UserSubscription current = getCurrentSubscription(user);
        if (current == null) {
            throw new NotFoundException("No active subscription found for user");
        }
        return resetSubscription(user, current.getGroup().getId());
    }

    @Transactional
    public UserSubscriptionView resetSubscription(User user, int groupId) {
        log.info("Resetting subscription for User {} with group {}", user.getId(), groupId);

        // Get the group - you'll need to inject GroupService into this class
        // For now, let's get it from the current subscription if groupId matches
        UserSubscription oldSubscription = getCurrentSubscription(user);
        Group group = null;

        if (oldSubscription != null && oldSubscription.getGroup() != null) {
            if (oldSubscription.getGroup().getId() == groupId) {
                group = oldSubscription.getGroup();
            }
        }

        if (group == null) {
            throw new NotFoundException("Group not found with id: " + groupId);
        }

        // Delete existing subscription
        userSubscriptionRepository.deleteByUserId(user.getId());
        userSubscriptionRepository.flush();

        // Create new subscription
        UserSubscription subscription = new UserSubscription();
        subscription.setUser(user);
        subscription.setGroup(group);
        subscription.setMultiLoginCount(group.getMultiLoginCount());
        subscription.setExpiresAt(LocalDateTime.now().plusDays(group.getDuration()));
        subscription.setDuration(group.getDuration());
        subscription.setPrice(group.getPrice());
        subscription.setGateway(GatewayName.FREE);
        subscription.setStatus(SubscriptionStatus.ACTIVE);

        UserSubscription saved = userSubscriptionRepository.save(subscription);
        radiusService.createUserRadChecks(saved);

        asyncNotificationHelper.sendSubscriptionWebhookAsync(saved, "USER_SUBSCRIPTION_RESET");

        return subscriptionViewMapper.toView(saved);
    }

    @Transactional
    public UserSubscriptionView resellerRenewSubscription(User user) {
        UserSubscription subscription = getCurrentSubscription(user);
        if (subscription == null) {
            throw new NotFoundException("No active subscription found for user");
        }
        return resellerRenewSubscription(user, subscription.getGroup());
    }

    @Retryable(retryFor = { DataIntegrityViolationException.class,
            ConcurrentSubscriptionException.class }, maxAttempts = 3, backoff = @Backoff(delay = 500))
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public UserSubscriptionView resellerRenewSubscription(User user, Group group) {
        log.info("Reseller renewing subscription for User {} with group {}",
                user.getId(), group.getId());

        try {
            // Try forceful deletion first
            // Try forceful deletion first
            userSubscriptionRepository.detachPayments(user.getId());
            int deleted = userSubscriptionRepository.deleteByUserIdForced(user.getId());
            log.info("Forcefully deleted {} subscriptions for user: {}", deleted, user.getId());
            userSubscriptionRepository.flush();
        } catch (Exception e) {
            log.warn("Error during forced deletion, trying standard deletion: {}", e.getMessage());
            // Fall back to standard deletion
            userSubscriptionRepository.deleteByUserId(user.getId());
            userSubscriptionRepository.flush();
        }

        // Create new subscription
        UserSubscription subscription = new UserSubscription();
        subscription.setUser(user);
        subscription.setGroup(group);
        subscription.setMultiLoginCount(group.getMultiLoginCount());
        subscription.setDuration(group.getDuration());
        subscription.setDailyBandwidth(group.getDailyBandwidth());
        subscription.setDownloadUpload(group.getDownloadUpload());
        subscription.setExpiresAt(LocalDateTime.now().plusDays(group.getDuration()));
        subscription.setGateway(GatewayName.RESELLER_CREDIT);
        subscription.setStatus(SubscriptionStatus.ACTIVE);

        UserSubscription saved = userSubscriptionRepository.save(subscription);
        radiusService.createUserRadChecks(saved);

        asyncNotificationHelper.sendSubscriptionWebhookAsync(saved, "USER_SUBSCRIPTION_RENEWED");

        return subscriptionViewMapper.toView(saved);
    }

    public UserSubscription getCurrentSubscription(User user) {
        return userSubscriptionRepository.findFirstByUserOrderByCreatedAtDesc(user);
    }

    private void extendSubscription(UserSubscription subscription, Integer days) {
        LocalDateTime currentExpiration = subscription.getExpiresAt();
        LocalDateTime newExpiration;

        if (currentExpiration != null && currentExpiration.isAfter(LocalDateTime.now())) {
            newExpiration = currentExpiration.plusDays(days);
        } else {
            newExpiration = LocalDateTime.now().plusDays(days);
        }

        subscription.setExpiresAt(newExpiration);
        userSubscriptionRepository.save(subscription);
        radiusService.updateUserExpirationRadCheck(subscription);
    }

    /**
     * Renews subscription by adding duration to existing expiration date
     * If subscription is expired, adds duration from today
     */
    public UserSubscriptionView renewSubscription(User user) {
        UserSubscription currentSubscription = getCurrentSubscription(user);
        if (currentSubscription == null) {
            throw new NotFoundException("No active subscription found for user: " + user.getUsername());
        }

        return renewSubscription(user, currentSubscription.getGroup());
    }

    /**
     * Renews subscription with a new group, adding duration to existing expiration
     */
    public UserSubscriptionView renewSubscription(User user, Group newGroup) {
        log.info("Renewing subscription for user: {} with group: {}", user.getUsername(), newGroup.getName());

        UserSubscription currentSubscription = getCurrentSubscription(user);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime newExpiresAt;

        if (currentSubscription != null && currentSubscription.getExpiresAt() != null) {
            // If current subscription exists and not expired, add to existing expiration
            LocalDateTime currentExpiration = currentSubscription.getExpiresAt();
            if (currentExpiration.isAfter(now)) {
                // Add to existing expiration
                newExpiresAt = currentExpiration.plusDays(newGroup.getDuration());
                log.info("Adding {} days to existing expiration. New expiration: {}",
                        newGroup.getDuration(), newExpiresAt);
            } else {
                // Subscription expired, add from today
                newExpiresAt = now.plusDays(newGroup.getDuration());
                log.info("Subscription expired. Adding {} days from today. New expiration: {}",
                        newGroup.getDuration(), newExpiresAt);
            }

            // Update existing subscription
            currentSubscription.setGroup(newGroup);
            currentSubscription.setExpiresAt(newExpiresAt);
            currentSubscription.setDuration(newGroup.getDuration());
            currentSubscription.setPrice(newGroup.getPrice());
            currentSubscription.setMultiLoginCount(newGroup.getMultiLoginCount());
            currentSubscription.setCanceled(false);
            currentSubscription.setStatus(SubscriptionStatus.ACTIVE);

            UserSubscription savedSubscription = userSubscriptionRepository.save(currentSubscription);
            radiusService.updateUserExpirationRadCheck(savedSubscription);

            asyncNotificationHelper.sendSubscriptionWebhookAsync(savedSubscription, "SUBSCRIPTION_RENEWED");

            return subscriptionViewMapper.toView(savedSubscription);

        } else {
            // No current subscription, create new one
            UserSubscription subscription = new UserSubscription();
            subscription.setUser(user);
            subscription.setGroup(newGroup);
            subscription.setMultiLoginCount(newGroup.getMultiLoginCount());
            subscription.setExpiresAt(now.plusDays(newGroup.getDuration()));
            subscription.setDuration(newGroup.getDuration());
            subscription.setPrice(newGroup.getPrice());
            subscription.setGateway(GatewayName.FREE);
            subscription.setStatus(SubscriptionStatus.ACTIVE);

            UserSubscription savedSubscription = userSubscriptionRepository.save(subscription);
            radiusService.createUserRadChecks(savedSubscription);

            asyncNotificationHelper.sendSubscriptionWebhookAsync(savedSubscription, "SUBSCRIPTION_CREATED");

            return subscriptionViewMapper.toView(savedSubscription);
        }
    }

    /**
     * Reseller reset - replaces subscription from today
     */
    public UserSubscriptionView resellerResetSubscription(User user) {
        return resetSubscription(user);
    }

    /**
     * Reseller reset with new group
     */
    public UserSubscriptionView resellerResetSubscription(User user, Group group) {
        return resetSubscription(user, group.getId());
    }
}