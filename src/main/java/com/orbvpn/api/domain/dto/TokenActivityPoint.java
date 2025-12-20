package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class TokenActivityPoint {
    private LocalDateTime timestamp;
    private BigDecimal earned;
    private BigDecimal spent;
}