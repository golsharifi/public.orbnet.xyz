package com.orbvpn.api.domain.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class GiftCardView {
    private Long id;
    private String code;
    private int groupId;
    private String groupName;
    private BigDecimal amount;
    private boolean used;
    private boolean cancelled;
    private LocalDateTime expirationDate;
    private LocalDateTime redeemedAt;
    private String redeemedByEmail;
    private LocalDateTime cancelledAt;
    private String cancelledByEmail;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}