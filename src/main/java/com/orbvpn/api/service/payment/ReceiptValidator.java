package com.orbvpn.api.service.payment;

import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.model.SubscriptionPurchase;
import com.orbvpn.api.exception.PaymentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReceiptValidator {

    @Value("${app.package.name}")
    private String packageName;

    @Value("${app.subscription.trial.enabled:false}")
    private boolean trialEnabled;

    @Value("${app.subscription.trial.days:3}")
    private int trialDays;

    @Value("${app.subscription.trial.products:#{new java.util.ArrayList()}}")
    private List<String> trialProducts;

    private final AndroidPublisher publisher;

    public void validateGooglePlayReceipt(String purchaseToken, String subscriptionId) {
        try {
            AndroidPublisher.Purchases.Subscriptions.Get request = publisher.purchases().subscriptions()
                    .get(packageName, subscriptionId, purchaseToken);

            SubscriptionPurchase purchase = request.execute();

            if (purchase == null) {
                throw new PaymentException("Invalid purchase receipt");
            }

            validatePaymentState(purchase);
            validateAcknowledgementState(purchase);

        } catch (Exception e) {
            log.error("Failed to validate Google Play receipt: {}", e.getMessage());
            throw new PaymentException("Receipt validation failed", e);
        }
    }

    private void validatePaymentState(SubscriptionPurchase purchase) {
        Integer paymentState = purchase.getPaymentState();
        if (paymentState == null || paymentState != 1) { // 1 means payment received
            throw new PaymentException("Invalid payment state: " + paymentState);
        }
    }

    private void validateAcknowledgementState(SubscriptionPurchase purchase) {
        Integer acknowledgementState = purchase.getAcknowledgementState();
        if (acknowledgementState != null && acknowledgementState == 1) {
            log.info("Purchase already acknowledged");
            return;
        }
    }

    public void validateTrialEligibility(String productId, Boolean isTrialPurchase) {
        if (Boolean.TRUE.equals(isTrialPurchase)) {
            if (!trialEnabled) {
                throw new PaymentException("Trials are currently disabled");
            }

            if (!trialProducts.contains(productId)) {
                throw new PaymentException("Product " + productId + " is not eligible for trial");
            }
        }
    }
}
