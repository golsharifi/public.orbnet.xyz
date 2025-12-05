package com.orbvpn.api.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StripeWebhookEvent {
    private String eventId;  // Stripe's unique event ID for idempotency
    private String type;
    private String subscriptionId;
    private String customerId;
    private String paymentIntentId;
    private String invoiceId;
    private String paymentMethodId;
    private String newPriceId;
    private String status;
    private Boolean cancelAtPeriodEnd;
    private LocalDateTime expiresAt;
    private LocalDateTime trialEnd;
    private Boolean isTrialPeriod;
    private String error;

    private Long amount;
    private String currency;
    private Boolean paid;

    private String plan;
    private String priceId;
    private String productId;
    private String interval;
    private Long intervalCount; // Changed from Integer to Long to match Stripe's type

    private String customerEmail;
    private String customerName;
}