package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class UserActivity {
    private Integer userId;
    private String username;
    private int activeConnections;
    private BigDecimal dataTransferred;
    private BigDecimal tokensSpent;
    private LocalDateTime lastActive;
}