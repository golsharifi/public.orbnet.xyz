package com.orbvpn.api.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * View of a referral level configuration for GraphQL responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferralLevelView {

    private Long id;

    /**
     * The level number (1 = direct referral, 2+ = indirect).
     */
    private int level;

    /**
     * Human-readable name for this level.
     */
    private String name;

    /**
     * Description of this level.
     */
    private String description;

    /**
     * Commission percentage for this level.
     */
    private BigDecimal commissionPercent;

    /**
     * Minimum tokens that can be earned at this level.
     */
    private BigDecimal minimumTokens;

    /**
     * Maximum tokens that can be earned per transaction.
     */
    private BigDecimal maximumTokens;

    /**
     * Whether this level is currently active.
     */
    private boolean active;
}
