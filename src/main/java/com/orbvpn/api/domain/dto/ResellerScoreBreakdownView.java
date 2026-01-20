package com.orbvpn.api.domain.dto;

import com.orbvpn.api.service.reseller.ResellerLevelService.ScoreBreakdown;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * DTO for exposing reseller score breakdown via GraphQL.
 * Provides transparency into how reseller levels are calculated.
 */
@Data
@Builder
public class ResellerScoreBreakdownView {
    private int resellerId;
    private String resellerEmail;
    private String currentLevel;
    private String totalScore;
    private boolean isOwner;

    // Monthly credit additions
    private String monthCreditScore;
    private String monthCreditValue;

    // Current account balance
    private String balanceScore;
    private String balanceValue;

    // Active subscriptions
    private String subscriptionScore;
    private Integer subscriptionCount;

    // Membership duration
    private String membershipScore;
    private Integer membershipDays;

    // Deposit frequency
    private String depositIntervalScore;

    // Lifetime sales
    private String lifetimeSalesScore;
    private String lifetimeSalesValue;

    // Monthly sales
    private String monthSalesScore;
    private String monthSalesValue;

    // Level recommendations
    private String recommendedLevel;
    private String nextLevelScore;
    private String pointsToNextLevel;

    /**
     * Converts internal ScoreBreakdown to GraphQL-friendly view.
     */
    public static ResellerScoreBreakdownView fromBreakdown(ScoreBreakdown breakdown,
            String recommendedLevel, BigDecimal nextLevelScore, BigDecimal pointsToNext) {

        return ResellerScoreBreakdownView.builder()
                .resellerId(breakdown.getResellerId())
                .resellerEmail(breakdown.getResellerEmail())
                .currentLevel(breakdown.getCurrentLevel())
                .totalScore(formatDecimal(breakdown.getTotalScore()))
                .isOwner(breakdown.isOwner())
                .monthCreditScore(formatDecimal(breakdown.getMonthCreditScore()))
                .monthCreditValue(formatDecimal(breakdown.getMonthCreditValue()))
                .balanceScore(formatDecimal(breakdown.getBalanceScore()))
                .balanceValue(formatDecimal(breakdown.getBalanceValue()))
                .subscriptionScore(formatDecimal(breakdown.getSubscriptionScore()))
                .subscriptionCount(breakdown.getSubscriptionCount())
                .membershipScore(formatDecimal(breakdown.getMembershipScore()))
                .membershipDays(breakdown.getMembershipDays())
                .depositIntervalScore(formatDecimal(breakdown.getDepositIntervalScore()))
                .lifetimeSalesScore(formatDecimal(breakdown.getLifetimeSalesScore()))
                .lifetimeSalesValue(formatDecimal(breakdown.getLifetimeSalesValue()))
                .monthSalesScore(formatDecimal(breakdown.getMonthSalesScore()))
                .monthSalesValue(formatDecimal(breakdown.getMonthSalesValue()))
                .recommendedLevel(recommendedLevel)
                .nextLevelScore(formatDecimal(nextLevelScore))
                .pointsToNextLevel(formatDecimal(pointsToNext))
                .build();
    }

    private static String formatDecimal(BigDecimal value) {
        return value != null ? value.setScale(2, java.math.RoundingMode.HALF_UP).toString() : null;
    }
}
