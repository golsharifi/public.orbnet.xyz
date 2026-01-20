package com.orbvpn.api.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    private String paymentId;
    private String clientSecret; // For Stripe
    private String checkoutUrl; // For PayPal/CoinPayment
    private String status;
    private String message;
    private Boolean requiresAction;
    private Boolean success;
    private String mobileProductId; // For App Store/Google Play
    private String subscriptionId;
    private Double amount;
    private String currency;
    private String gatewayReference;
    private String qrcodeUrl;
}