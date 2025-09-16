package com.orbvpn.api.domain.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class StripePaymentResponse {
  private String clientSecret;
  private String paymentIntentId;
  private String subscriptionId;
  private Boolean requiresAction;
  private String error;
  private StripeSubscriptionData subscription;
}