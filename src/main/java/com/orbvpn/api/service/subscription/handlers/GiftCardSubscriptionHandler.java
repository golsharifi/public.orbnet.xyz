package com.orbvpn.api.service.subscription.handlers;

import com.orbvpn.api.domain.entity.Group;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.domain.enums.GatewayName;
import com.orbvpn.api.domain.enums.SubscriptionStatus;
import com.orbvpn.api.repository.UserSubscriptionRepository;
import com.orbvpn.api.service.AsyncNotificationHelper;
import com.orbvpn.api.service.GroupService;
import com.orbvpn.api.service.subscription.payment.PaymentProcessor;
import com.orbvpn.api.service.RadiusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class GiftCardSubscriptionHandler implements SubscriptionHandler {
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final PaymentProcessor paymentProcessor;
    private final RadiusService radiusService;
    private final AsyncNotificationHelper asyncNotificationHelper;
    private final GroupService groupService;

    @Override
    @Transactional
    public void handleSubscription(User user, int groupId, LocalDateTime expiresAt,
            String token, Boolean isTrialPeriod, String subscriptionId) {

        log.info("Processing gift card subscription for user: {} with group: {}",
                user.getId(), groupId);

        try {
            Group group = groupService.getById(groupId);

            // Check for existing subscription
            UserSubscription existingSubscription = userSubscriptionRepository
                    .findFirstByUserOrderByCreatedAtDesc(user);

            UserSubscription subscription;
            LocalDateTime newExpirationDate;

            if (existingSubscription != null &&
                    existingSubscription.getExpiresAt() != null &&
                    existingSubscription.getExpiresAt().isAfter(LocalDateTime.now())) {

                log.info("Found existing active subscription for user: {}. Adding gift card duration.",
                        user.getId());

                // Add the new duration to the existing expiration date
                newExpirationDate = existingSubscription.getExpiresAt()
                        .plusDays(group.getDuration());

                // Update existing subscription
                subscription = existingSubscription;
                subscription.setExpiresAt(newExpirationDate);
                subscription.setGroup(group); // Update to new group
                subscription.setMultiLoginCount(group.getMultiLoginCount());
                subscription.setDailyBandwidth(group.getDailyBandwidth());
                subscription.setDownloadUpload(group.getDownloadUpload());

            } else {
                log.info("No active subscription found for user: {}. Creating new subscription.",
                        user.getId());

                // Calculate expiration for new subscription
                newExpirationDate = LocalDateTime.now().plusDays(group.getDuration());

                // Create new subscription
                subscription = new UserSubscription();
                subscription.setUser(user);
                subscription.setGroup(group);
                subscription.setExpiresAt(newExpirationDate);
                subscription.setMultiLoginCount(group.getMultiLoginCount());
                subscription.setDuration(group.getDuration());
                subscription.setDailyBandwidth(group.getDailyBandwidth());
                subscription.setDownloadUpload(group.getDownloadUpload());
                subscription.setStatus(SubscriptionStatus.ACTIVE);
                subscription.setAutoRenew(false);
                subscription.setCanceled(false);
            }

            // Set common properties
            subscription.setGateway(GatewayName.GIFT_CARD);
            subscription.setPrice(group.getPrice());

            // Save the subscription
            UserSubscription savedSubscription = userSubscriptionRepository.save(subscription);

            // Create payment record
            paymentProcessor.processPayment(user, savedSubscription, GatewayName.GIFT_CARD);

            // Update radius checks
            radiusService.updateUserExpirationRadCheck(savedSubscription);

            // Send webhook event asynchronously
            asyncNotificationHelper.sendSubscriptionWebhookAsync(savedSubscription,
                    existingSubscription != null ? "SUBSCRIPTION_UPDATED" : "SUBSCRIPTION_CREATED");

            log.info("Successfully processed gift card subscription for user: {}. New expiration date: {}",
                    user.getId(), newExpirationDate);

        } catch (Exception e) {
            log.error("Error processing gift card subscription for user: {} - Error: {}",
                    user.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to process gift card subscription", e);
        }
    }

    @Override
    public GatewayName getGatewayType() {
        return GatewayName.GIFT_CARD;
    }
}