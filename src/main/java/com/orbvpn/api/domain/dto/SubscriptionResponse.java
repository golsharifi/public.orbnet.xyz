package com.orbvpn.api.domain.dto;

public class SubscriptionResponse {

    private boolean success;
    private String message;
    private SubscriptionStatusDTO subscriptionStatus;

    // Constructor for success/failure only
    public SubscriptionResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    // Constructor for detailed response
    public SubscriptionResponse(boolean success, String message, SubscriptionStatusDTO subscriptionStatus) {
        this.success = success;
        this.message = message;
        this.subscriptionStatus = subscriptionStatus;
    }

    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public SubscriptionStatusDTO getSubscriptionStatus() {
        return subscriptionStatus;
    }

    public void setSubscriptionStatus(SubscriptionStatusDTO subscriptionStatus) {
        this.subscriptionStatus = subscriptionStatus;
    }
}