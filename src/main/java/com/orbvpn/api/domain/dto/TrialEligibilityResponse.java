package com.orbvpn.api.domain.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrialEligibilityResponse {
    private boolean eligible;
    private String reason;
    private LocalDateTime lastTrialDate;
    private String platform;
}