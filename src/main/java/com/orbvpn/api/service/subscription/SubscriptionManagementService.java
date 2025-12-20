package com.orbvpn.api.service.subscription;

import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.domain.dto.UserSubscriptionView;
import com.orbvpn.api.exception.NotFoundException;
import com.orbvpn.api.mapper.UserSubscriptionViewMapper;
import com.orbvpn.api.repository.UserSubscriptionRepository;
import com.orbvpn.api.service.AsyncNotificationHelper;
import com.orbvpn.api.service.RadiusService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SubscriptionManagementService {
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final UserSubscriptionService subscriptionService;
    private final RadiusService radiusService;
    private final AsyncNotificationHelper asyncNotificationHelper;
    private final UserSubscriptionViewMapper subscriptionViewMapper;

    /**
     * Completely removes a user's subscription
     */
    public UserSubscriptionView removeUserSubscription(User user) {
        log.info("Removing subscription for User {}", user.getId());

        UserSubscription currentSubscription = subscriptionService.getCurrentSubscription(user);
        if (currentSubscription == null) {
            log.warn("No subscription found to remove for User {}", user.getId());
            throw new NotFoundException("No subscription found for user");
        }

        // Delete the subscription from the database
        userSubscriptionRepository.deleteByUserId(user.getId());
        userSubscriptionRepository.flush();

        // Delete associated radius checks
        radiusService.deleteUserRadChecks(user);

        // Create webhook event for subscription removal (async)
        asyncNotificationHelper.sendSubscriptionWebhookAsync(currentSubscription, "SUBSCRIPTION_REMOVED");

        log.info("Successfully removed subscription for User {}", user.getId());
        return subscriptionViewMapper.toView(currentSubscription);
    }

    /**
     * Reverts to previous subscription state by preserving remaining time
     */
    public UserSubscriptionView revertLastSubscriptionChange(User user) {
        log.info("Reverting last subscription change for User {}", user.getId());

        UserSubscription currentSubscription = subscriptionService.getCurrentSubscription(user);
        if (currentSubscription == null) {
            log.warn("No subscription found to revert for User {}", user.getId());
            throw new NotFoundException("No subscription found for user");
        }

        LocalDateTime now = LocalDateTime.now();

        // Calculate remaining time from current subscription
        LocalDateTime originalExpiryDate = now.plusDays(
                ChronoUnit.DAYS.between(now, currentSubscription.getExpiresAt()));

        UserSubscription revertedSubscription = createRevertedSubscription(
                currentSubscription, originalExpiryDate);

        saveRevertedSubscription(user, revertedSubscription);

        log.info("Successfully reverted subscription for User {} to original expiry date {}",
                user.getId(), originalExpiryDate);

        return subscriptionViewMapper.toView(revertedSubscription);
    }

    /**
     * Reverts a subscription to a specific number of remaining days
     */
    public UserSubscriptionView revertSubscriptionToDays(User user, int remainingDays) {
        log.info("Reverting subscription for User {} to {} remaining days", user.getId(), remainingDays);

        UserSubscription currentSubscription = subscriptionService.getCurrentSubscription(user);
        if (currentSubscription == null) {
            log.warn("No subscription found to revert for User {}", user.getId());
            throw new NotFoundException("No subscription found for user");
        }

        LocalDateTime newExpiryDate = LocalDateTime.now().plusDays(remainingDays);

        UserSubscription revertedSubscription = createRevertedSubscription(
                currentSubscription, newExpiryDate);

        saveRevertedSubscription(user, revertedSubscription);

        log.info("Successfully reverted subscription for User {} to expire in {} days",
                user.getId(), remainingDays);

        return subscriptionViewMapper.toView(revertedSubscription);
    }

    /**
     * Helper method to create a reverted subscription with new expiry date
     */
    private UserSubscription createRevertedSubscription(UserSubscription currentSubscription,
            LocalDateTime expiryDate) {
        UserSubscription revertedSubscription = new UserSubscription();
        revertedSubscription.setUser(currentSubscription.getUser());
        revertedSubscription.setGroup(currentSubscription.getGroup());
        revertedSubscription.setMultiLoginCount(currentSubscription.getMultiLoginCount());
        revertedSubscription.setDailyBandwidth(currentSubscription.getDailyBandwidth());
        revertedSubscription.setDownloadUpload(currentSubscription.getDownloadUpload());
        revertedSubscription.setAutoRenew(currentSubscription.getAutoRenew());
        revertedSubscription.setExpiresAt(expiryDate);
        revertedSubscription.setIsTrialPeriod(currentSubscription.getIsTrialPeriod());
        revertedSubscription.setPendingGroupId(currentSubscription.getPendingGroupId());
        revertedSubscription.setGateway(currentSubscription.getGateway());
        revertedSubscription.setDuration(currentSubscription.getDuration());
        revertedSubscription.setPrice(currentSubscription.getPrice());
        revertedSubscription.setCanceled(currentSubscription.isCanceled());
        revertedSubscription.setPurchaseToken(currentSubscription.getPurchaseToken());
        revertedSubscription.setOriginalTransactionId(currentSubscription.getOriginalTransactionId());
        revertedSubscription.setStripeCustomerId(currentSubscription.getStripeCustomerId());
        revertedSubscription.setStripeSubscriptionId(currentSubscription.getStripeSubscriptionId());
        revertedSubscription.setWeeklyAdsWatched(currentSubscription.getWeeklyAdsWatched());
        revertedSubscription.setLastWeeklyReset(currentSubscription.getLastWeeklyReset());

        return revertedSubscription;
    }

    /**
     * Helper method to save reverted subscription and update related services
     */
    private void saveRevertedSubscription(User user, UserSubscription revertedSubscription) {
        // Remove current subscription
        userSubscriptionRepository.deleteByUserId(user.getId());
        userSubscriptionRepository.flush();

        // Save the reverted subscription
        userSubscriptionRepository.save(revertedSubscription);

        // Update radius checks
        radiusService.updateUserExpirationRadCheck(revertedSubscription);

        // Send webhook event (async)
        asyncNotificationHelper.sendSubscriptionWebhookAsync(revertedSubscription, "SUBSCRIPTION_REVERTED");
    }
}