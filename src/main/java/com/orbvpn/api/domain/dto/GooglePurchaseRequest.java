package com.orbvpn.api.domain.dto;

import lombok.Data;

@Data
public class GooglePurchaseRequest {
    private String packageName;
    private String purchaseToken;
    private String subscriptionId;
}