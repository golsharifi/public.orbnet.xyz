package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import com.orbvpn.api.domain.enums.PaymentCategory;

@Data
@Builder
public class PaymentRequest {
    private BigDecimal amount;
    private PaymentCategory category;
    private Integer userId;
    private Long planId;
    private Integer quantity;
    private String currency;
    private boolean subscription;
    private String returnUrl;
    private String cancelUrl;
}