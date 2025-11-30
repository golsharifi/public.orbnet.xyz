package com.orbvpn.api.domain.dto;

import com.stripe.model.SubscriptionItem;
import java.util.List;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StripeSubscriptionData {
    private String id;
    private String subscriptionId;
    private int groupId;
    private LocalDateTime expiresAt;
    private boolean isTrialPeriod;
    private String customerId;
    private String status;
    private LocalDateTime currentPeriodEnd;
    private Boolean cancelAtPeriodEnd;
    private List<SubscriptionItem> items;

    public static StripeSubscriptionData.Builder builder() {
        return new StripeSubscriptionData.Builder();
    }

    public static class Builder {
        private final StripeSubscriptionData data = new StripeSubscriptionData();

        public Builder withGroupId(int groupId) {
            data.setGroupId(groupId);
            return this;
        }

        public Builder withSubscriptionId(String subscriptionId) {
            data.setSubscriptionId(subscriptionId);
            return this;
        }

        // Add other builder methods as needed

        public StripeSubscriptionData build() {
            return data;
        }

        public Builder errors(List<Object> errors) {
            return null;
        }
    }

    // Setter for items
    public void setItems(List<SubscriptionItem> items) {
        this.items = items;
    }
}