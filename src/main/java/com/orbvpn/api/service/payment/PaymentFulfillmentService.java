package com.orbvpn.api.service.payment;

import com.orbvpn.api.domain.entity.Payment;
import com.orbvpn.api.domain.entity.Group;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.domain.enums.PaymentCategory;
import com.orbvpn.api.domain.enums.PaymentStatus;
import com.orbvpn.api.exception.PaymentException;
import com.orbvpn.api.repository.PaymentRepository;
import com.orbvpn.api.repository.UserSubscriptionRepository;
import com.orbvpn.api.service.AsyncNotificationHelper;
import com.orbvpn.api.service.GroupService;
import com.orbvpn.api.service.RadiusService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Exception.class) // Change this
public class PaymentFulfillmentService {

    private final PaymentRepository paymentRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final GroupService groupService;
    private final RadiusService radiusService;
    private final AsyncNotificationHelper asyncNotificationHelper;

    @Transactional(rollbackFor = Exception.class)
    public void fulfillPayment(Payment payment) {
        // Find fresh instance of payment to avoid stale data
        Payment freshPayment = paymentRepository.findById(payment.getId())
                .orElseThrow(() -> new PaymentException("Payment not found"));

        if (freshPayment.getStatus() == PaymentStatus.SUCCEEDED) {
            log.info("Payment {} is already fulfilled, skipping", freshPayment.getId());
            return;
        }

        try {
            if (freshPayment.getCategory() == PaymentCategory.GROUP) {
                handleGroupPayment(freshPayment);
            }

            freshPayment.setStatus(PaymentStatus.SUCCEEDED);
            paymentRepository.save(freshPayment);

            log.info("Payment {} fulfilled successfully", freshPayment.getId());
        } catch (Exception e) {
            log.error("Error fulfilling payment {}", freshPayment.getId(), e);
            freshPayment.setStatus(PaymentStatus.FAILED);
            freshPayment.setErrorMessage(e.getMessage());
            paymentRepository.save(freshPayment);
            throw new PaymentException("Payment fulfillment failed", e);
        }
    }

    private void handleGroupPayment(Payment payment) {
        Group group = groupService.getGroupIgnoreDelete(payment.getGroupId());
        int userId = payment.getUser().getId();

        // Check for existing active subscription
        Optional<UserSubscription> existingSubscriptionOpt =
                userSubscriptionRepository.findCurrentSubscription(userId);

        LocalDateTime newExpiresAt;

        if (existingSubscriptionOpt.isPresent()) {
            UserSubscription existingSubscription = existingSubscriptionOpt.get();

            // Check if renewing same group or changing to different group
            if (existingSubscription.getGroup().getId() == group.getId()) {
                // Same group - extend from current expiration (industry standard)
                LocalDateTime currentExpiry = existingSubscription.getExpiresAt();
                if (currentExpiry != null && currentExpiry.isAfter(LocalDateTime.now())) {
                    // User has remaining time, extend from current expiry
                    newExpiresAt = currentExpiry.plusDays(group.getDuration());
                    log.info("Extending subscription for user {} from {} to {} (+{} days)",
                            userId, currentExpiry, newExpiresAt, group.getDuration());
                } else {
                    // Subscription expired, start fresh from now
                    newExpiresAt = LocalDateTime.now().plusDays(group.getDuration());
                }
            } else {
                // Different group - replace subscription (plan change)
                // Start fresh from now (user is upgrading/downgrading)
                newExpiresAt = LocalDateTime.now().plusDays(group.getDuration());
                log.info("User {} changing plan from group {} to group {}",
                        userId, existingSubscription.getGroup().getId(), group.getId());
            }

            // Delete old subscription to replace with new one
            userSubscriptionRepository.deleteByUserId(userId);
            userSubscriptionRepository.flush();
        } else {
            // No existing subscription, start fresh
            newExpiresAt = LocalDateTime.now().plusDays(group.getDuration());
        }

        // Update payment expiration
        payment.setExpiresAt(newExpiresAt);

        // Create new subscription
        UserSubscription subscription = new UserSubscription();
        subscription.setUser(payment.getUser());
        subscription.setGroup(group);
        subscription.setPayment(payment);
        subscription.setExpiresAt(newExpiresAt);
        subscription.setMultiLoginCount(group.getMultiLoginCount());
        subscription.setCanceled(false);
        subscription.setAutoRenew(true);
        subscription.setDuration(group.getDuration());
        subscription.setPrice(payment.getPrice());

        userSubscriptionRepository.saveAndFlush(subscription);

        try {
            radiusService.updateUserExpirationRadCheck(subscription);

            // Add webhook notification for new subscription (async to avoid blocking)
            asyncNotificationHelper.sendSubscriptionWebhookAsync(subscription, "SUBSCRIPTION_CREATED");

            // Send payment success notification to user
            asyncNotificationHelper.sendPaymentSuccessNotificationAsync(
                    payment.getUser(),
                    subscription,
                    payment.getPrice()
            );

        } catch (Exception e) {
            log.error("Failed to update radius records: {}", e.getMessage());
            throw e;
        }
    }
}