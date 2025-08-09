package com.orbvpn.api.service.payment;

import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.model.SubscriptionPurchasesAcknowledgeRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PurchaseAcknowledgementService {
    private static final String PACKAGE_NAME = "com.orbvpn.android";
    private final AndroidPublisher publisher;

    public void acknowledgePurchase(String subscriptionId, String purchaseToken) {
        try {
            // First check if already acknowledged
            AndroidPublisher.Purchases.Subscriptions.Get getRequest = publisher.purchases()
                    .subscriptions()
                    .get(PACKAGE_NAME, subscriptionId, purchaseToken);

            var purchase = getRequest.execute();
            if (purchase == null) {
                log.warn("Purchase not found for acknowledgment");
                return;
            }

            if (purchase.getAcknowledgementState() != null && purchase.getAcknowledgementState() == 1) {
                log.info("Purchase already acknowledged: {}", purchaseToken);
                return;
            }

            // Acknowledge the purchase
            publisher.purchases()
                    .subscriptions()
                    .acknowledge(PACKAGE_NAME, subscriptionId, purchaseToken,
                            new SubscriptionPurchasesAcknowledgeRequest())
                    .execute();

            log.info("Successfully acknowledged purchase: {}", purchaseToken);
        } catch (Exception e) {
            if (e.getMessage() != null && (e.getMessage().contains("productNotOwnedByUser") ||
                    e.getMessage().contains("Purchase already acknowledged"))) {
                log.info("Acknowledgment not needed: {}", e.getMessage());
                return;
            }
            log.error("Error acknowledging purchase: {}", e.getMessage(), e);
        }
    }
}