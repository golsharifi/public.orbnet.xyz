package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class TransactionView {
    private String id;
    private String userId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String gateway;
    private LocalDateTime transactionDate;
    private String description;
}
