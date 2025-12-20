package com.orbvpn.api.domain.enums;

public enum NotificationCategory {
    BILLING("Billing related notifications"),
    SECURITY("Security alerts and updates"),
    SUPPORT("Support related messages"),
    PROMOTIONAL("Marketing and promotional content"),
    SYSTEM("System updates and maintenance"),
    ACCOUNT("Account related notifications"),
    SUBSCRIPTION("Subscription status and updates");

    private final String description;

    NotificationCategory(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}