package com.orbvpn.api.service.payment;

import com.orbvpn.api.domain.entity.Payment;
import com.orbvpn.api.domain.entity.Group;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.domain.enums.PaymentCategory;
import com.orbvpn.api.domain.enums.PaymentStatus;
import com.orbvpn.api.exception.PaymentException;
import com.orbvpn.api.repository.PaymentRepository;
import com.orbvpn.api.repository.UserSubscriptionRepository;
import com.orbvpn.api.service.GroupService;
import com.orbvpn.api.service.RadiusService;
import com.orbvpn.api.service.webhook.WebhookEventCreator;
import com.orbvpn.api.service.webhook.WebhookService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Exception.class) // Change this
public class PaymentFulfillmentService {

    private final PaymentRepository paymentRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final GroupService groupService;
    private final RadiusService radiusService;
    private final WebhookService webhookService;
    private final WebhookEventCreator webhookEventCreator;

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

        // Delete any existing subscriptions for this user
        userSubscriptionRepository.deleteByUserId(payment.getUser().getId());
        userSubscriptionRepository.flush();

        // Set expiration if not set
        if (payment.getExpiresAt() == null) {
            payment.setExpiresAt(LocalDateTime.now().plusDays(group.getDuration()));
        }

        // Create new subscription
        UserSubscription subscription = new UserSubscription();
        subscription.setUser(payment.getUser());
        subscription.setGroup(group);
        subscription.setPayment(payment);
        subscription.setExpiresAt(payment.getExpiresAt());
        subscription.setMultiLoginCount(group.getMultiLoginCount());
        subscription.setCanceled(false);
        subscription.setAutoRenew(true);
        subscription.setDuration(group.getDuration());
        subscription.setPrice(payment.getPrice());

        userSubscriptionRepository.saveAndFlush(subscription);

        try {
            radiusService.updateUserExpirationRadCheck(subscription);

            // Add webhook notification for new subscription
            webhookService.processWebhook("SUBSCRIPTION_CREATED",
                    webhookEventCreator.createSubscriptionPayload(subscription));

        } catch (Exception e) {
            log.error("Failed to update radius records: {}", e.getMessage());
            throw e;
        }
    }
}