package com.orbvpn.api.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceCalculation {
    private BigDecimal basePrice;
    private BigDecimal loyaltyDiscount;
    private BigDecimal bulkDiscount;
    private BigDecimal finalPrice;
    private String currency;
}