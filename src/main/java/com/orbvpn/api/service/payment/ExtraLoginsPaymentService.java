package com.orbvpn.api.service.payment;

import com.orbvpn.api.domain.entity.ExtraLoginsPlan;
import com.orbvpn.api.domain.entity.Payment;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.enums.GatewayName;
import com.orbvpn.api.domain.enums.PaymentCategory;
import com.orbvpn.api.domain.enums.PaymentStatus;
import com.orbvpn.api.exception.PaymentException;
import com.orbvpn.api.service.ExtraLoginsService;
import com.orbvpn.api.repository.PaymentRepository;
import com.orbvpn.api.domain.dto.PriceCalculation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExtraLoginsPaymentService {
    private final PaymentRepository paymentRepository;
    private final ExtraLoginsService extraLoginsService;
    private final ObjectMapper objectMapper; // Inject ObjectMapper

    @Transactional
    public Payment createPayment(User user, ExtraLoginsPlan plan, int quantity, String paymentMethod) {
        PriceCalculation priceCalc = extraLoginsService.calculatePrice(plan.getId(), quantity);

        Payment payment = Payment.builder()
                .user(user)
                .status(PaymentStatus.PENDING)
                .gateway(GatewayName.valueOf(paymentMethod))
                .category(PaymentCategory.MORE_LOGIN)
                .price(priceCalc.getFinalPrice())
                .moreLoginCount(plan.getLoginCount() * quantity)
                .groupId(plan.getId().intValue())
                .build();

        if (plan.isSubscription()) {
            payment.setRenew(true);
        }

        Map<String, Object> metadata = new HashMap<>();
        if (priceCalc.getBulkDiscount().compareTo(BigDecimal.ZERO) > 0) {
            metadata.put("bulkDiscount", priceCalc.getBulkDiscount());
        }
        if (priceCalc.getLoyaltyDiscount().compareTo(BigDecimal.ZERO) > 0) {
            metadata.put("loyaltyDiscount", priceCalc.getLoyaltyDiscount());
        }

        if (!metadata.isEmpty()) {
            try {
                payment.setMetaData(objectMapper.writeValueAsString(metadata));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize payment metadata", e);
            }
        }

        payment = paymentRepository.save(payment);
        log.info("Created payment {} for extra logins plan {} with price {}",
                payment.getId(), plan.getId(), priceCalc.getFinalPrice());

        return payment;
    }

    @Transactional
    public void handlePaymentSuccess(Payment payment) {
        try {
            extraLoginsService.handlePaymentSuccess(payment);
            payment.setStatus(PaymentStatus.SUCCEEDED);
            paymentRepository.save(payment);
            log.info("Successfully processed payment {}", payment.getId());
        } catch (Exception e) {
            log.error("Failed to process extra logins payment {}", payment.getId(), e);
            payment.setStatus(PaymentStatus.FAILED);
            payment.setErrorMessage(e.getMessage());
            paymentRepository.save(payment);
            throw new PaymentException("Failed to process payment: " + e.getMessage());
        }
    }

    @Transactional
    public void handleSubscriptionCancellation(String subscriptionId) {
        extraLoginsService.cancelSubscription(subscriptionId);
        log.info("Cancelled subscription {}", subscriptionId);
    }

    @Transactional
    public void handlePaymentFailure(Payment payment, String errorMessage) {
        payment.setStatus(PaymentStatus.FAILED);
        payment.setErrorMessage(errorMessage);
        paymentRepository.save(payment);
        log.error("Payment {} failed: {}", payment.getId(), errorMessage);
    }
}