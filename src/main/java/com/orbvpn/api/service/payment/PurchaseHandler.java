package com.orbvpn.api.service.payment;

import com.google.api.services.androidpublisher.model.SubscriptionPurchase;
import com.orbvpn.api.domain.entity.User;

public interface PurchaseHandler {
    void handlePurchase(SubscriptionPurchase purchase, User user, String subscriptionId, String purchaseToken);
}
