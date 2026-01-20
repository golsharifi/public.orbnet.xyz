package com.orbvpn.api.domain.dto.staticip;

import com.orbvpn.api.domain.entity.StaticIPSubscription;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaticIPSubscriptionResponse {
    private boolean success;
    private String message;
    private StaticIPSubscription subscription;
    private String paymentUrl;
    private String mobileProductId;  // Product ID for in-app purchase (iOS/Android)
}
