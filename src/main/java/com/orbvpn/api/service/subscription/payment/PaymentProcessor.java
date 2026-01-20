package com.orbvpn.api.service.subscription.payment;

import com.orbvpn.api.domain.entity.Payment;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.domain.enums.GatewayName;
import com.orbvpn.api.domain.enums.PaymentCategory;
import com.orbvpn.api.domain.enums.PaymentStatus;
import com.orbvpn.api.repository.PaymentRepository;
import com.orbvpn.api.repository.UserSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentProcessor {
    private final PaymentRepository paymentRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;

    @Transactional
    public Payment processPayment(User user, UserSubscription subscription, GatewayName gateway) {
        log.info("Processing payment for user: {} with gateway: {}", user.getId(), gateway);

        // Create and save payment first
        Payment payment = createPayment(user, subscription, gateway);
        Payment savedPayment = paymentRepository.save(payment);

        // Now update the subscription with the saved payment
        subscription.setPayment(savedPayment);
        userSubscriptionRepository.save(subscription);

        return savedPayment;
    }

    @Transactional
    public Payment processPayment(Payment payment) {
        log.info("Processing pre-created payment for user: {}", payment.getUser().getId());

        Payment savedPayment = paymentRepository.save(payment);

        if (payment.getSubscriptionId() != null) {
            UserSubscription subscription = userSubscriptionRepository
                    .findById(Integer.valueOf(payment.getSubscriptionId()))
                    .orElse(null);

            if (subscription != null) {
                subscription.setPayment(savedPayment);
                userSubscriptionRepository.save(subscription);
            }
        }

        return savedPayment;
    }

    private Payment createPayment(User user, UserSubscription subscription, GatewayName gateway) {
        Payment payment = Payment.builder()
                .user(user)
                .groupId(subscription.getGroup().getId())
                .price(subscription.getGroup().getPrice())
                .status(PaymentStatus.SUCCEEDED)
                .gateway(gateway)
                .category(PaymentCategory.GROUP)
                .renew(true)
                .expiresAt(subscription.getExpiresAt())
                .moreLoginCount(subscription.getGroup().getMultiLoginCount())
                .version(0L) // Initialize version
                .build();

        return payment;
    }
}
