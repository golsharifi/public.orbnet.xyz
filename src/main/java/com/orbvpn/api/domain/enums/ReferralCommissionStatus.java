package com.orbvpn.api.domain.enums;

/**
 * Status of a referral commission.
 */
public enum ReferralCommissionStatus {
    /**
     * Commission calculated but not yet credited to user's token balance.
     */
    PENDING,

    /**
     * Commission has been credited to user's token balance.
     */
    CREDITED,

    /**
     * Commission failed to be credited (e.g., user account issues).
     */
    FAILED,

    /**
     * Commission was cancelled (e.g., payment refunded).
     */
    CANCELLED
}
