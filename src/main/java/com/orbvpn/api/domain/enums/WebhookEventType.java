package com.orbvpn.api.domain.enums;

public enum WebhookEventType {
    // Authentication Events
    LOGIN_SUCCESS("auth.login.success", "User login successful"),
    LOGIN_FAILED("auth.login.failed", "User login failed"),
    LOGOUT("auth.logout", "User logged out"),

    // User Account Events
    USER_CREATED("user.created", "User account created"),
    USER_UPDATED("user.updated", "User account updated"),
    USER_DELETED("user.deleted", "User account deleted"),
    USER_ACTIVATED("user.activated", "User account activated"),
    USER_DEACTIVATED("user.deactivated", "User account deactivated"),
    USER_SOFT_DELETED("user.soft_deleted", "User account soft deleted"),
    USER_ACCOUNT_DELETED("user.account_deleted", "User deleted their own account"),
    USER_EMAIL_CHANGED("user.email_changed", "User email changed"),
    USER_PROFILE_UPDATED("user.profile_updated", "User profile updated"),

    // Password Events
    PASSWORD_CHANGED("user.password.changed", "User password changed"),
    PASSWORD_RESET_REQUESTED("user.password.reset_requested", "Password reset requested"),
    PASSWORD_RESET_COMPLETED("user.password.reset_completed", "Password reset completed"),
    PASSWORD_REENCRYPTED("user.password.reencrypted", "Password re-encrypted"),

    // Subscription Events
    SUBSCRIPTION_CREATED("subscription.created", "New subscription created"),
    SUBSCRIPTION_RENEWED("subscription.renewed", "Subscription renewed"),
    SUBSCRIPTION_CANCELLED("subscription.cancelled", "Subscription cancelled"),
    SUBSCRIPTION_EXPIRED("subscription.expired", "Subscription expired"),
    SUBSCRIPTION_UPDATED("subscription.updated", "Subscription updated"),
    SUBSCRIPTION_TRIAL_STARTED("subscription.trial.started", "Trial subscription started"),
    SUBSCRIPTION_TRIAL_ENDED("subscription.trial.ended", "Trial subscription ended"),
    SUBSCRIPTION_AUTO_RENEW_ENABLED("subscription.auto_renew.enabled", "Auto-renewal enabled"),
    SUBSCRIPTION_AUTO_RENEW_DISABLED("subscription.auto_renew.disabled", "Auto-renewal disabled"),
    SUBSCRIPTION_PLAN_CHANGED("subscription.plan.changed", "Subscription plan changed"),
    SUBSCRIPTION_REMOVED("subscription.removed", "Subscription removed from user"),
    SUBSCRIPTION_REVERTED("subscription.reverted", "Subscription reverted to previous state"),

    // Payment Events
    PAYMENT_CREATED("payment.created", "Payment created"),
    PAYMENT_PENDING("payment.pending", "Payment pending"),
    PAYMENT_SUCCEEDED("payment.succeeded", "Payment successful"),
    PAYMENT_FAILED("payment.failed", "Payment failed"),
    PAYMENT_REFUNDED("payment.refunded", "Payment refunded"),
    PAYMENT_DISPUTED("payment.disputed", "Payment disputed"),
    PAYMENT_CANCELLED("payment.cancelled", "Payment cancelled"),

    // Apple Payment Events
    APPLE_PAYMENT_RECEIVED("payment.apple.received", "Apple payment received"),
    APPLE_SUBSCRIPTION_RENEWED("payment.apple.renewed", "Apple subscription renewed"),
    APPLE_SUBSCRIPTION_CANCELLED("payment.apple.cancelled", "Apple subscription cancelled"),

    // Google Play Payment Events
    GOOGLE_PAYMENT_RECEIVED("payment.google.received", "Google Play payment received"),
    GOOGLE_SUBSCRIPTION_RENEWED("payment.google.renewed", "Google Play subscription renewed"),
    GOOGLE_SUBSCRIPTION_CANCELLED("payment.google.cancelled", "Google Play subscription cancelled"),

    // Device Events
    DEVICE_CONNECTED("device.connected", "Device connected"),
    DEVICE_DISCONNECTED("device.disconnected", "Device disconnected"),
    DEVICE_BLOCKED("device.blocked", "Device blocked"),
    DEVICE_ADDED("device.added", "New device added"),
    DEVICE_REMOVED("device.removed", "Device removed"),
    DEVICE_LIMIT_REACHED("device.limit.reached", "Device limit reached"),

    // Support/Ticket Events
    TICKET_CREATED("support.ticket.created", "Support ticket created"),
    TICKET_UPDATED("support.ticket.updated", "Support ticket updated"),
    TICKET_CLOSED("support.ticket.closed", "Support ticket closed"),
    TICKET_REOPENED("support.ticket.reopened", "Support ticket reopened"),
    TICKET_REPLIED("support.ticket.replied", "Reply added to ticket"),
    TICKET_ASSIGNED("support.ticket.assigned", "Ticket assigned to agent"),

    // Bandwidth/Usage Events
    BANDWIDTH_EXCEEDED("usage.bandwidth.exceeded", "Bandwidth limit exceeded"),
    BANDWIDTH_WARNING("usage.bandwidth.warning", "Bandwidth warning threshold reached"),
    USAGE_REPORT("usage.report.generated", "Usage report generated"),
    DAILY_LIMIT_REACHED("usage.daily.limit.reached", "Daily usage limit reached"),

    // Reseller Events
    RESELLER_CREATED("reseller.created", "New reseller created"),
    RESELLER_UPDATED("reseller.updated", "Reseller information updated"),
    RESELLER_DELETED("reseller.deleted", "Reseller deleted"),
    RESELLER_CREDIT_ADDED("reseller.credit.added", "Credit added to reseller"),
    RESELLER_CREDIT_DEDUCTED("reseller.credit.deducted", "Credit deducted from reseller"),

    // System Events
    SYSTEM_ERROR("system.error", "System error occurred"),
    SYSTEM_WARNING("system.warning", "System warning occurred"),
    SYSTEM_MAINTENANCE("system.maintenance", "System maintenance notification"),
    SYSTEM_UPDATE("system.update", "System update notification"),
    API_ERROR("system.api.error", "API error occurred"),
    DATABASE_ERROR("system.database.error", "Database error occurred"),

    // Extra Logins Events
    EXTRA_LOGINS_PURCHASED("extra_logins.purchased", "Extra logins purchased"),
    EXTRA_LOGINS_GIFTED("extra_logins.gifted", "Extra logins gifted to user"),
    EXTRA_LOGINS_EXPIRING("extra_logins.expiring", "Extra logins about to expire"),
    EXTRA_LOGINS_EXPIRED("extra_logins.expired", "Extra logins expired"),
    EXTRA_LOGINS_CANCELLED("extra_logins.cancelled", "Extra logins cancelled"),
    EXTRA_LOGINS_ACTIVATED("extra_logins.activated", "Extra logins activated"),
    EXTRA_LOGINS_DEACTIVATED("extra_logins.deactivated", "Extra logins deactivated"),

    // Email/Notification Events
    EMAIL_SENT("notification.email.sent", "Email notification sent"),
    SMS_SENT("notification.sms.sent", "SMS notification sent"),
    PUSH_SENT("notification.push.sent", "Push notification sent"),
    NOTIFICATION_FAILED("notification.failed", "Notification delivery failed");

    private final String eventName;
    private final String description;

    WebhookEventType(String eventName, String description) {
        this.eventName = eventName;
        this.description = description;
    }

    public String getEventName() {
        return eventName;
    }

    public String getDescription() {
        return description;
    }

    public static WebhookEventType fromEventName(String eventName) {
        for (WebhookEventType type : values()) {
            if (type.getEventName().equals(eventName)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown event name: " + eventName);
    }

    public String getCategory() {
        return this.eventName.split("\\.")[0];
    }

    public boolean isUserEvent() {
        return getCategory().equals("user") || getCategory().equals("auth");
    }

    public boolean isSubscriptionEvent() {
        return getCategory().equals("subscription");
    }

    public boolean isPaymentEvent() {
        return getCategory().equals("payment");
    }

    public boolean isDeviceEvent() {
        return getCategory().equals("device");
    }

    public boolean isSupportEvent() {
        return getCategory().equals("support");
    }

    public boolean isSystemEvent() {
        return getCategory().equals("system");
    }

    public boolean isNotificationEvent() {
        return getCategory().equals("notification");
    }

    public boolean isUsageEvent() {
        return getCategory().equals("usage");
    }

    public boolean isResellerEvent() {
        return getCategory().equals("reseller");
    }
}