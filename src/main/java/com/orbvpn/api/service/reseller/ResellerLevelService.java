package com.orbvpn.api.service.reseller;

import com.orbvpn.api.domain.entity.Reseller;
import com.orbvpn.api.domain.entity.ResellerLevel;
import com.orbvpn.api.domain.entity.ResellerLevelCoefficients;
import com.orbvpn.api.domain.enums.ResellerLevelName;
import com.orbvpn.api.repository.*;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.DAYS;

/**
 * Service for calculating and updating reseller levels based on performance scores.
 *
 * The scoring system evaluates resellers on multiple metrics:
 * - Monthly credit additions
 * - Current account balance
 * - Active subscriptions
 * - Membership duration
 * - Deposit frequency
 * - Lifetime sales
 * - Monthly sales
 *
 * Scores are calculated relative to the average of all other resellers,
 * which allows dynamic adjustment as the reseller base grows.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResellerLevelService {

    private static final BigDecimal BIG_DECIMAL_100 = BigDecimal.valueOf(100);
    private static final BigDecimal BIG_DECIMAL_HALF = new BigDecimal("0.5");
    private static final BigDecimal MIN_DIVISOR = new BigDecimal("0.01"); // Prevent division by zero

    // Grace period: resellers must be below threshold for this many consecutive checks before demotion
    private static final int DEMOTION_GRACE_CHECKS = 2;

    private final UserSubscriptionRepository userSubscriptionRepository;
    private final ResellerRepository resellerRepository;
    private final ResellerLevelRepository resellerLevelRepository;
    private final ResellerAddCreditRepository resellerAddCreditRepository;
    private final ResellerLevelCoefficientsRepository resellerLevelCoefficientsRepository;

    /**
     * Updates all reseller levels that are due for recalculation (level set > 1 month ago).
     * Called by scheduled job (JobService) every hour.
     */
    @Transactional
    public void updateResellersLevel() {
        log.info("Starting reseller level update job");

        LocalDateTime monthBefore = LocalDateTime.now().minusMonths(1L);
        List<Reseller> resellers = resellerRepository.findByLevelSetDateBefore(monthBefore);

        if (resellers.isEmpty()) {
            log.info("No resellers due for level update");
            return;
        }

        log.info("Found {} resellers due for level update", resellers.size());

        ResellerLevelCoefficients coefficients = getCoefficientsOrDefault();
        if (coefficients == null) {
            log.error("Cannot update reseller levels - coefficients not configured");
            return;
        }

        // Pre-calculate averages once for efficiency (fixes N+1 query issue)
        AverageMetrics averages = calculateAverageMetrics(coefficients);

        int promotions = 0;
        int demotions = 0;
        int unchanged = 0;

        for (Reseller reseller : resellers) {
            try {
                ResellerLevel oldLevel = reseller.getLevel();
                ScoreBreakdown breakdown = calculateScoreBreakdown(reseller, coefficients, averages);
                ResellerLevel newLevel = determineLevel(breakdown.getTotalScore());

                if (newLevel.getName() != oldLevel.getName()) {
                    boolean isPromotion = newLevel.getMinScore().compareTo(oldLevel.getMinScore()) > 0;

                    if (isPromotion) {
                        // Promotion: apply immediately and reset grace count
                        reseller.setLevel(newLevel);
                        reseller.setLevelSetDate(LocalDateTime.now());
                        reseller.setDemotionGraceCount(0);
                        promotions++;
                        logLevelChange(reseller, oldLevel, newLevel, breakdown, "PROMOTION");
                    } else {
                        // Demotion: check grace period
                        int currentGraceCount = reseller.getDemotionGraceCount() + 1;
                        reseller.setDemotionGraceCount(currentGraceCount);

                        if (currentGraceCount >= DEMOTION_GRACE_CHECKS) {
                            // Grace period exhausted - apply demotion
                            reseller.setLevel(newLevel);
                            reseller.setLevelSetDate(LocalDateTime.now());
                            reseller.setDemotionGraceCount(0);
                            demotions++;
                            logLevelChange(reseller, oldLevel, newLevel, breakdown, "DEMOTION");
                        } else {
                            // Still in grace period - warn but don't demote yet
                            reseller.setLevelSetDate(LocalDateTime.now());
                            unchanged++;
                            log.warn("RESELLER_LEVEL_GRACE: Reseller {} ({}) scored for {} but remains at {} " +
                                    "(grace count: {}/{}). Score: {}",
                                    reseller.getId(),
                                    reseller.getUser().getEmail(),
                                    newLevel.getName(),
                                    oldLevel.getName(),
                                    currentGraceCount,
                                    DEMOTION_GRACE_CHECKS,
                                    breakdown.getTotalScore());
                        }
                    }
                } else {
                    // Score qualifies for current level - reset grace count
                    reseller.setLevelSetDate(LocalDateTime.now());
                    if (reseller.getDemotionGraceCount() > 0) {
                        log.info("Reseller {} grace count reset (score recovered)", reseller.getId());
                        reseller.setDemotionGraceCount(0);
                    }
                    unchanged++;
                    log.debug("Reseller {} remains at level {} with score {}",
                            reseller.getId(), oldLevel.getName(), breakdown.getTotalScore());
                }
            } catch (Exception e) {
                log.error("Failed to update level for reseller {}: {}",
                        reseller.getId(), e.getMessage(), e);
            }
        }

        resellerRepository.saveAll(resellers);

        log.info("Reseller level update complete. Promotions: {}, Demotions: {}, Unchanged: {}",
                promotions, demotions, unchanged);
    }

    /**
     * Calculates the detailed score breakdown for a reseller.
     * This method is also exposed for transparency - admins can see why a reseller has their current score.
     */
    public ScoreBreakdown calculateScoreBreakdown(Reseller reseller) {
        ResellerLevelCoefficients coefficients = getCoefficientsOrDefault();
        AverageMetrics averages = calculateAverageMetrics(coefficients);
        return calculateScoreBreakdown(reseller, coefficients, averages);
    }

    private ScoreBreakdown calculateScoreBreakdown(Reseller reseller,
            ResellerLevelCoefficients coefficients, AverageMetrics averages) {

        ResellerLevel level = reseller.getLevel();
        if (level.getName().equals(ResellerLevelName.OWNER)) {
            return ScoreBreakdown.builder()
                    .resellerId(reseller.getId())
                    .resellerEmail(reseller.getUser().getEmail())
                    .currentLevel(level.getName().name())
                    .totalScore(BigDecimal.valueOf(999999)) // OWNER always max
                    .isOwner(true)
                    .build();
        }

        LocalDateTime lastSetDate = reseller.getLevelSetDate();
        LocalDateTime now = LocalDateTime.now();

        // 1. Monthly Credit Score
        BigDecimal monthCredit = safeGetValue(
                resellerAddCreditRepository.getResellerCreditAfterDate(reseller, lastSetDate));
        BigDecimal monthCreditScore = calculateScore(
                monthCredit,
                averages.getAvgMonthCredit(),
                coefficients.getMonthCreditPercent(),
                coefficients.getMonthCreditMax());

        // 2. Balance Score
        BigDecimal balance = safeGetValue(reseller.getCredit());
        BigDecimal balanceScore = calculateScore(
                balance,
                averages.getAvgBalance(),
                coefficients.getCurrentCreditPercent(),
                coefficients.getCurrentCreditMax());

        // 3. Active Subscription Score
        BigDecimal activeSubscriptions = BigDecimal.valueOf(
                userSubscriptionRepository.countResellerActiveSubscriptions(reseller));
        BigDecimal subscriptionScore = calculateScore(
                activeSubscriptions,
                averages.getAvgActiveSubscriptions(),
                coefficients.getActiveSubscriptionPercent(),
                coefficients.getActiveSubscriptionMax());

        // 4. Membership Duration Score
        BigDecimal membershipDays = BigDecimal.valueOf(
                Math.max(1, DAYS.between(reseller.getCreatedAt(), now)));
        BigDecimal membershipScore = calculateScore(
                membershipDays,
                averages.getAvgMembershipDays(),
                coefficients.getMembershipDurationPercent(),
                coefficients.getMembershipDurationMax());

        // 5. Deposit Interval Score (rewards frequent deposits)
        BigDecimal depositIntervalScore = calculateDepositIntervalScore(
                reseller, membershipDays, coefficients);

        // 6. Lifetime Sales Score
        LocalDateTime epochStart = LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.UTC);
        BigDecimal lifetimeSales = safeGetValue(
                userSubscriptionRepository.getResellerTotalSale(reseller, epochStart));
        BigDecimal lifetimeSalesScore = calculateScore(
                lifetimeSales,
                averages.getAvgLifetimeSales(),
                coefficients.getTotalSalePercent(),
                coefficients.getTotalSaleMax());

        // 7. Monthly Sales Score
        BigDecimal monthSales = safeGetValue(
                userSubscriptionRepository.getResellerTotalSale(reseller, lastSetDate));
        BigDecimal monthSalesScore = calculateScore(
                monthSales,
                averages.getAvgMonthSales(),
                coefficients.getMonthSalePercent(),
                coefficients.getMonthSaleMax());

        // Calculate total score
        BigDecimal totalScore = monthCreditScore
                .add(balanceScore)
                .add(subscriptionScore)
                .add(membershipScore)
                .add(depositIntervalScore)
                .add(lifetimeSalesScore)
                .add(monthSalesScore);

        return ScoreBreakdown.builder()
                .resellerId(reseller.getId())
                .resellerEmail(reseller.getUser().getEmail())
                .currentLevel(level.getName().name())
                .totalScore(totalScore)
                .monthCreditScore(monthCreditScore)
                .monthCreditValue(monthCredit)
                .balanceScore(balanceScore)
                .balanceValue(balance)
                .subscriptionScore(subscriptionScore)
                .subscriptionCount(activeSubscriptions.intValue())
                .membershipScore(membershipScore)
                .membershipDays(membershipDays.intValue())
                .depositIntervalScore(depositIntervalScore)
                .lifetimeSalesScore(lifetimeSalesScore)
                .lifetimeSalesValue(lifetimeSales)
                .monthSalesScore(monthSalesScore)
                .monthSalesValue(monthSales)
                .isOwner(false)
                .build();
    }

    /**
     * Pre-calculates average metrics across all non-OWNER resellers.
     * This optimizes the N+1 query problem by fetching all data once.
     */
    private AverageMetrics calculateAverageMetrics(ResellerLevelCoefficients coefficients) {
        List<Reseller> resellers = resellerRepository.findAll()
                .stream()
                .filter(it -> it.getLevel().getName() != ResellerLevelName.OWNER)
                .collect(Collectors.toList());

        // Need at least 1 reseller to calculate averages
        BigDecimal resellersCount = BigDecimal.valueOf(Math.max(1, resellers.size()));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime monthAgo = now.minusMonths(1);
        LocalDateTime epochStart = LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.UTC);

        // Calculate totals
        BigDecimal totalBalance = BigDecimal.ZERO;
        BigDecimal totalMembershipDays = BigDecimal.ZERO;

        for (Reseller r : resellers) {
            totalBalance = totalBalance.add(safeGetValue(r.getCredit()));
            long days = Math.max(1, DAYS.between(r.getCreatedAt(), now));
            totalMembershipDays = totalMembershipDays.add(BigDecimal.valueOf(days));
        }

        // Get aggregated values from repository
        BigDecimal totalMonthCredit = safeGetValue(
                resellerAddCreditRepository.getAllResellersTotalCreditAfterDate(monthAgo));
        BigDecimal totalActiveSubscriptions = BigDecimal.valueOf(
                userSubscriptionRepository.countAllResellersActiveSubscriptions());
        BigDecimal totalLifetimeSales = safeGetValue(
                userSubscriptionRepository.getAllResellerTotalSale(epochStart));
        BigDecimal totalMonthSales = safeGetValue(
                userSubscriptionRepository.getAllResellerTotalSale(monthAgo));

        return AverageMetrics.builder()
                .avgMonthCredit(safeDivide(totalMonthCredit, resellersCount))
                .avgBalance(safeDivide(totalBalance, resellersCount))
                .avgActiveSubscriptions(safeDivide(totalActiveSubscriptions, resellersCount))
                .avgMembershipDays(safeDivide(totalMembershipDays, resellersCount))
                .avgLifetimeSales(safeDivide(totalLifetimeSales, resellersCount))
                .avgMonthSales(safeDivide(totalMonthSales, resellersCount))
                .totalResellers(resellers.size())
                .build();
    }

    /**
     * Calculates deposit interval score - rewards frequent deposits.
     * Score increases as deposit frequency increases.
     */
    private BigDecimal calculateDepositIntervalScore(Reseller reseller,
            BigDecimal membershipDays, ResellerLevelCoefficients coefficients) {

        BigDecimal deposits = safeGetValue(resellerAddCreditRepository.countResellerDeposits(reseller));

        // If no deposits, return 0 score
        if (deposits.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        // Calculate average days between deposits
        BigDecimal depositInterval = safeDivide(membershipDays, deposits);

        BigDecimal depositIntervalManualDays = safeGetValue(coefficients.getDepositIntervalManualDays());
        BigDecimal depositIntervalMax = safeGetValue(coefficients.getDepositIntervalMax());

        // Score formula: more frequent deposits = higher score
        // If interval < manualDays, score increases above base
        // If interval > manualDays, score decreases below base
        BigDecimal intervalScore = depositIntervalMax
                .add(depositIntervalManualDays.subtract(depositInterval).multiply(BIG_DECIMAL_HALF));

        // Clamp to [0, max]
        if (intervalScore.compareTo(BigDecimal.ZERO) < 0) {
            intervalScore = BigDecimal.ZERO;
        }
        if (intervalScore.compareTo(depositIntervalMax) > 0) {
            intervalScore = depositIntervalMax;
        }

        return intervalScore;
    }

    /**
     * Determines the appropriate level based on total score.
     */
    private ResellerLevel determineLevel(BigDecimal totalScore) {
        List<ResellerLevel> allLevels = resellerLevelRepository.findAll();

        // Sort by minScore ascending (STARTER first, OWNER last)
        allLevels.sort(Comparator.comparing(ResellerLevel::getMinScore));

        // Find highest level where score >= minScore (excluding OWNER)
        ResellerLevel bestLevel = allLevels.get(0); // Default to lowest level

        for (ResellerLevel level : allLevels) {
            // Skip OWNER - it can only be assigned manually
            if (level.getName().equals(ResellerLevelName.OWNER)) {
                continue;
            }
            if (totalScore.compareTo(level.getMinScore()) >= 0) {
                bestLevel = level;
            }
        }

        return bestLevel;
    }

    /**
     * Calculates a score component based on value relative to average.
     * Formula: (value / avgValue) × max × percent / 100
     *
     * @param value The reseller's value for this metric
     * @param avgValue The average value across all resellers
     * @param coefPercent The weight percentage for this metric (0-100)
     * @param max The maximum score for this component
     * @return Score between 0 and max
     */
    public BigDecimal calculateScore(BigDecimal value, BigDecimal avgValue,
            BigDecimal coefPercent, BigDecimal max) {

        value = safeGetValue(value);
        avgValue = safeGetValue(avgValue);
        coefPercent = safeGetValue(coefPercent);
        max = safeGetValue(max);

        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        // Ensure we don't divide by zero
        if (avgValue.compareTo(MIN_DIVISOR) < 0) {
            avgValue = MIN_DIVISOR;
        }

        BigDecimal score = value
                .divide(avgValue, 4, RoundingMode.HALF_UP)
                .multiply(max)
                .multiply(coefPercent)
                .divide(BIG_DECIMAL_100, 2, RoundingMode.HALF_UP);

        // Cap at maximum
        if (score.compareTo(max) > 0) {
            return max;
        }

        return score;
    }

    /**
     * Logs level changes for audit trail.
     */
    private void logLevelChange(Reseller reseller, ResellerLevel oldLevel,
            ResellerLevel newLevel, ScoreBreakdown breakdown, String changeType) {

        log.info("RESELLER_LEVEL_{}: Reseller {} ({}) changed from {} to {}. " +
                 "Score: {} | Breakdown: monthCredit={}, balance={}, subscriptions={}, " +
                 "membership={}, depositInterval={}, lifetimeSales={}, monthSales={}",
                changeType,
                reseller.getId(),
                reseller.getUser().getEmail(),
                oldLevel.getName(),
                newLevel.getName(),
                breakdown.getTotalScore(),
                breakdown.getMonthCreditScore(),
                breakdown.getBalanceScore(),
                breakdown.getSubscriptionScore(),
                breakdown.getMembershipScore(),
                breakdown.getDepositIntervalScore(),
                breakdown.getLifetimeSalesScore(),
                breakdown.getMonthSalesScore());
    }

    /**
     * Gets coefficients from database or returns default values.
     */
    private ResellerLevelCoefficients getCoefficientsOrDefault() {
        try {
            return resellerLevelCoefficientsRepository.findById(1).orElse(null);
        } catch (Exception e) {
            log.error("Failed to load reseller level coefficients: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Safe division that handles null and zero divisors.
     */
    private BigDecimal safeDivide(BigDecimal dividend, BigDecimal divisor) {
        dividend = safeGetValue(dividend);
        divisor = safeGetValue(divisor);

        if (divisor.compareTo(MIN_DIVISOR) < 0) {
            divisor = MIN_DIVISOR;
        }

        return dividend.divide(divisor, 4, RoundingMode.HALF_UP);
    }

    /**
     * Returns the value or ZERO if null.
     */
    private BigDecimal safeGetValue(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    /**
     * DTO containing pre-calculated average metrics for efficiency.
     */
    @Data
    @Builder
    public static class AverageMetrics {
        private BigDecimal avgMonthCredit;
        private BigDecimal avgBalance;
        private BigDecimal avgActiveSubscriptions;
        private BigDecimal avgMembershipDays;
        private BigDecimal avgLifetimeSales;
        private BigDecimal avgMonthSales;
        private int totalResellers;
    }

    /**
     * Detailed breakdown of a reseller's score.
     * Used for transparency and debugging.
     */
    @Data
    @Builder
    public static class ScoreBreakdown {
        private int resellerId;
        private String resellerEmail;
        private String currentLevel;
        private BigDecimal totalScore;
        private boolean isOwner;

        // Individual component scores
        private BigDecimal monthCreditScore;
        private BigDecimal monthCreditValue;

        private BigDecimal balanceScore;
        private BigDecimal balanceValue;

        private BigDecimal subscriptionScore;
        private int subscriptionCount;

        private BigDecimal membershipScore;
        private int membershipDays;

        private BigDecimal depositIntervalScore;

        private BigDecimal lifetimeSalesScore;
        private BigDecimal lifetimeSalesValue;

        private BigDecimal monthSalesScore;
        private BigDecimal monthSalesValue;
    }
}
