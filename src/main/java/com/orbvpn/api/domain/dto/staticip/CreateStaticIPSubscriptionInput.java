package com.orbvpn.api.domain.dto.staticip;

import com.orbvpn.api.domain.enums.StaticIPPlanType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateStaticIPSubscriptionInput {
    private StaticIPPlanType planType;
    private String paymentMethod;
    private String selectedCoin;
    private boolean autoRenew = true;
}
