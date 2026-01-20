package com.orbvpn.api.constants;

public final class AppleNotificationConstants {
    private AppleNotificationConstants() {
    }

    public static class NotificationType {
        public static final String INITIAL_BUY = "INITIAL_BUY";
        public static final String DID_RECOVER = "DID_RECOVER";
        public static final String DID_RENEW = "DID_RENEW";
        public static final String EXPIRED = "EXPIRED";
        public static final String DID_CHANGE_RENEWAL_STATUS = "DID_CHANGE_RENEWAL_STATUS";
        public static final String DID_CHANGE_RENEWAL_PREF = "DID_CHANGE_RENEWAL_PREF";
        public static final String CANCEL = "CANCEL";
        public static final String DID_FAIL_TO_RENEW = "DID_FAIL_TO_RENEW";
        public static final String PRICE_INCREASE = "PRICE_INCREASE";
        public static final String REFUND = "REFUND";
        public static final String REVOKE = "REVOKE";
    }

    public static final class WebhookEvents {
        public static final String SUBSCRIPTION_CREATED = "SUBSCRIPTION_CREATED";
        public static final String SUBSCRIPTION_RENEWED = "SUBSCRIPTION_RENEWED";
        public static final String SUBSCRIPTION_CANCELLED = "SUBSCRIPTION_CANCELLED";
        public static final String SUBSCRIPTION_EXPIRED = "SUBSCRIPTION_EXPIRED";
        public static final String SUBSCRIPTION_RENEWAL_FAILED = "SUBSCRIPTION_RENEWAL_FAILED";
    }
}
