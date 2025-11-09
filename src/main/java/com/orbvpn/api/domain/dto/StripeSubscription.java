package com.orbvpn.api.domain.dto;

import lombok.Data;
import lombok.experimental.Accessors;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class StripeSubscription {
    private String id;
    private String status;
    private LocalDateTime currentPeriodEnd;
    private Boolean cancelAtPeriodEnd;
}