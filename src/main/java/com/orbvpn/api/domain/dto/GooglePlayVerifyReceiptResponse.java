package com.orbvpn.api.domain.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GooglePlayVerifyReceiptResponse {
    private String expiryTimeMillis; // Expiration time of the subscription
    private String productId; // The SKU of the product
    private String orderId; // Order ID for the purchase
}