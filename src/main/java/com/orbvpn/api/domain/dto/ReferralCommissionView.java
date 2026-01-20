package com.orbvpn.api.domain.dto;

import com.orbvpn.api.domain.enums.ReferralCommissionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * View of a single referral commission for GraphQL responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferralCommissionView {

    private Long id;

    /**
     * The user who triggered the commission (without exposing sensitive data).
     */
    private String sourceUserEmail;

    /**
     * The referral level (1 = direct, 2+ = indirect).
     */
    private int level;

    /**
     * The original payment amount.
     */
    private BigDecimal paymentAmount;

    /**
     * The commission percentage applied.
     */
    private BigDecimal commissionPercent;

    /**
     * Tokens earned from this commission.
     */
    private BigDecimal tokenAmount;

    /**
     * Current status of the commission.
     */
    private ReferralCommissionStatus status;

    /**
     * When the commission was created.
     */
    private LocalDateTime createdAt;

    /**
     * When the tokens were credited (if applicable).
     */
    private LocalDateTime creditedAt;
}
