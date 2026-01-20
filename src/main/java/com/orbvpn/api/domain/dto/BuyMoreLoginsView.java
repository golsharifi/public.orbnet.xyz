package com.orbvpn.api.domain.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class BuyMoreLoginsView {
  private BigDecimal price;
  private String message;
  private Boolean eligibleForLoyaltyDiscount;
  private BigDecimal loyaltyDiscountPercent;
  private Boolean eligibleForBulkDiscount;
  private BigDecimal bulkDiscountPercent;
}