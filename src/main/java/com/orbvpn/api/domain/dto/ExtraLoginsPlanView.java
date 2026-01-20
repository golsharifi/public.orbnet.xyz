package com.orbvpn.api.domain.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ExtraLoginsPlanView {
    private Long id;
    private String name;
    private String description;
    private int loginCount;
    private BigDecimal basePrice;
    private int durationDays;
    private boolean subscription;
    private boolean giftable;
    private String mobileProductId;
    private BigDecimal bulkDiscountPercent;
    private int minimumQuantity;
}