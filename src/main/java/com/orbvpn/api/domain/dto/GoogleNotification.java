package com.orbvpn.api.domain.dto;

import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleNotification {
    private String packageName;
    private long eventTimeMillis;
    private SubscriptionNotification subscriptionNotification;
    private VoidedPurchaseNotification voidedPurchaseNotification; // Add this for voided purchase notifications

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SubscriptionNotification {
        private String purchaseToken;
        private String subscriptionId;
        private int notificationType;
        private String version;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VoidedPurchaseNotification {
        private String purchaseToken;
        private String orderId;
        private int productType;
        private int refundType;
    }
}