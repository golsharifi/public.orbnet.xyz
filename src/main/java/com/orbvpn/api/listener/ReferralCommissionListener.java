package com.orbvpn.api.listener;

import com.orbvpn.api.domain.entity.Payment;
import com.orbvpn.api.domain.entity.ReferralCommission;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.event.SubscriptionChangedEvent;
import com.orbvpn.api.service.referral.ReferralMLMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * Listens for subscription events and processes referral commissions
 * when a payment-based subscription is created.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReferralCommissionListener {

    private final ReferralMLMService referralMLMService;

    /**
     * Handle subscription changes to process MLM referral commissions.
     * Runs after the transaction commits to ensure payment data is persisted.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleSubscriptionForReferralCommission(SubscriptionChangedEvent event) {
        try {
            // Only process new subscriptions that have a payment
            if (!"NEW_SUBSCRIPTION".equals(event.getEventType())) {
                return;
            }

            UserSubscription subscription = event.getSubscription();
            if (subscription == null) {
                log.debug("No subscription in event, skipping referral processing");
                return;
            }

            Payment payment = subscription.getPayment();
            if (payment == null) {
                log.debug("Subscription {} has no payment, skipping referral processing",
                        subscription.getId());
                return;
            }

            User user = event.getUser();
            if (user == null) {
                log.warn("No user in subscription event, skipping referral processing");
                return;
            }

            // Check if user was referred
            if (user.getReferredBy() == null) {
                log.debug("User {} has no referrer, skipping referral commission",
                        user.getId());
                return;
            }

            log.info("Processing referral commissions for user {} subscription payment {}",
                    user.getId(), payment.getId());

            List<ReferralCommission> commissions = referralMLMService.processPaymentCommissions(
                    payment, user);

            if (!commissions.isEmpty()) {
                log.info("Created {} referral commissions for payment {}",
                        commissions.size(), payment.getId());
            }

        } catch (Exception e) {
            log.error("Error processing referral commissions for subscription event: {}",
                    e.getMessage(), e);
            // Don't rethrow - referral processing should not affect the main flow
        }
    }
}
