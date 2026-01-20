package com.orbvpn.api.domain.enums;

public enum SubscriptionStatus {
    ACTIVE,
    EXPIRED,
    CANCELLED,
    PENDING,
    TRIAL,
    GRACE_PERIOD,
    PAUSED,
    REVOKED,
    ON_HOLD,
    VOIDED,
    REFUNDED,
    PAYMENT_FAILED,
    VERIFICATION_FAILED,
    PENDING_VERIFICATION;

    @Override
    public String toString() {
        // Ensure status string never exceeds database column length
        return name().length() <= 20 ? name() : name().substring(0, 20);
    }
}
