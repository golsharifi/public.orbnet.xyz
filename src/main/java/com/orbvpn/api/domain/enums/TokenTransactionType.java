package com.orbvpn.api.domain.enums;

public enum TokenTransactionType {
    EARN, // From watching ads
    SPEND, // From using VPN
    STAKE, // For staking tokens
    UNSTAKE, // For unstaking tokens
    STAKE_REWARD, // For staking rewards
    REFERRAL, // From MLM referral commissions

    // Credit/Wallet related transactions
    CREDIT_DEPOSIT, // From credit card or payment gateway deposit
    SUBSCRIPTION_PURCHASE, // Spending tokens/credit to buy subscription
    DEVICE_PURCHASE, // Spending tokens/credit to buy extra devices
    GIFT_CARD_REDEEM, // Credit from redeeming a gift card
    ADMIN_CREDIT, // Credit added by admin
    ADMIN_DEBIT, // Credit deducted by admin
    REFUND // Refund to user's credit balance
}