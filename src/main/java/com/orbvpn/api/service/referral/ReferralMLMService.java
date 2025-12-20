package com.orbvpn.api.service.referral;

import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.domain.enums.ReferralCommissionStatus;
import com.orbvpn.api.domain.enums.TokenTransactionType;
import com.orbvpn.api.repostitory.ReferralCommissionRepository;
import com.orbvpn.api.repostitory.ReferralConfigRepository;
import com.orbvpn.api.repostitory.ReferralLevelRepository;
import com.orbvpn.api.service.AdTokenServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Multi-Level Marketing (MLM) Referral Service.
 *
 * Handles the commission calculation and token crediting for the referral system.
 * When a user makes a payment, their referrer chain is traversed and commissions
 * are calculated at each level based on configured percentages.
 *
 * Features:
 * - Multi-level commission calculation
 * - Configurable token rate
 * - Qualification requirements (active subscription)
 * - Cooling period for new accounts
 * - Daily/monthly earning caps
 * - Minimum payment threshold
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReferralMLMService {

    private final ReferralLevelRepository referralLevelRepository;
    private final ReferralCommissionRepository referralCommissionRepository;
    private final ReferralConfigRepository configRepository;
    private final AdTokenServiceImpl tokenService;

    /**
     * Process referral commissions for a successful payment.
     * This method traverses the referral chain and awards tokens to each referrer
     * up to the maximum configured level.
     *
     * @param payment The completed payment
     * @param payingUser The user who made the payment
     * @return List of created commissions
     */
    @Transactional
    public List<ReferralCommission> processPaymentCommissions(Payment payment, User payingUser) {
        List<ReferralCommission> commissions = new ArrayList<>();

        if (payment == null || payingUser == null) {
            log.warn("Cannot process commissions: payment or user is null");
            return commissions;
        }

        // Get configuration
        ReferralConfig config = configRepository.getConfig();

        // Check if referral system is enabled
        if (!config.isEnabled()) {
            log.debug("Referral system is disabled");
            return commissions;
        }

        BigDecimal paymentAmount = payment.getPrice();
        if (paymentAmount == null || paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("Payment amount {} is not eligible for commissions", paymentAmount);
            return commissions;
        }

        // Check minimum payment amount
        if (config.getMinimumPaymentAmount() != null &&
                paymentAmount.compareTo(config.getMinimumPaymentAmount()) < 0) {
            log.debug("Payment amount {} below minimum {} for commissions",
                    paymentAmount, config.getMinimumPaymentAmount());
            return commissions;
        }

        // Get maximum level configured
        Integer maxLevel = referralLevelRepository.findMaxActiveLevel();
        if (maxLevel == null || maxLevel == 0) {
            log.debug("No active referral levels configured");
            return commissions;
        }

        // Apply config max levels if set
        if (config.getMaxLevels() > 0 && config.getMaxLevels() < maxLevel) {
            maxLevel = config.getMaxLevels();
        }

        BigDecimal tokenRate = config.getTokenRate();
        log.info("Processing referral commissions for payment {} (amount: {}) from user {} with token rate {}",
                payment.getId(), paymentAmount, payingUser.getId(), tokenRate);

        // Traverse the referral chain
        User currentReferrer = payingUser.getReferredBy();
        int currentLevel = 1;

        while (currentReferrer != null && currentLevel <= maxLevel) {
            // Check for self-referral loop (shouldn't happen but safety check)
            if (currentReferrer.getId() == payingUser.getId()) {
                log.warn("Self-referral loop detected for user {}", payingUser.getId());
                break;
            }

            // Check if commission already exists for this payment and beneficiary
            if (referralCommissionRepository.existsByPaymentIdAndBeneficiaryId(
                    (long) payment.getId(), currentReferrer.getId())) {
                log.debug("Commission already exists for payment {} and beneficiary {}",
                        payment.getId(), currentReferrer.getId());
                currentReferrer = currentReferrer.getReferredBy();
                currentLevel++;
                continue;
            }

            // Check qualification: beneficiary must pass all requirements
            String disqualifyReason = checkBeneficiaryQualification(currentReferrer, config);
            if (disqualifyReason != null) {
                log.info("Beneficiary {} disqualified for level {}: {}",
                        currentReferrer.getId(), currentLevel, disqualifyReason);
                currentReferrer = currentReferrer.getReferredBy();
                currentLevel++;
                continue;
            }

            // Get the level configuration
            Optional<ReferralLevel> levelConfig = referralLevelRepository.findByLevelAndActiveTrue(currentLevel);
            if (levelConfig.isEmpty()) {
                log.debug("No active configuration for level {}", currentLevel);
                currentReferrer = currentReferrer.getReferredBy();
                currentLevel++;
                continue;
            }

            ReferralLevel level = levelConfig.get();

            // Calculate token reward
            BigDecimal tokenAmount = level.calculateTokenReward(paymentAmount, tokenRate);

            // Apply earning caps
            tokenAmount = applyEarningCaps(currentReferrer, tokenAmount, config);

            if (tokenAmount.compareTo(BigDecimal.ZERO) > 0) {
                // Create commission record
                ReferralCommission commission = ReferralCommission.builder()
                        .beneficiary(currentReferrer)
                        .sourceUser(payingUser)
                        .payment(payment)
                        .level(currentLevel)
                        .paymentAmount(paymentAmount)
                        .commissionPercent(level.getCommissionPercent())
                        .tokenAmount(tokenAmount)
                        .tokenRate(tokenRate)
                        .status(ReferralCommissionStatus.PENDING)
                        .build();

                commission = referralCommissionRepository.save(commission);
                commissions.add(commission);

                log.info("Created commission {} for user {} at level {} - {} tokens from payment {}",
                        commission.getId(), currentReferrer.getId(), currentLevel,
                        tokenAmount, payment.getId());

                // Credit tokens immediately
                try {
                    creditCommission(commission);
                } catch (Exception e) {
                    log.error("Failed to credit commission {}: {}", commission.getId(), e.getMessage(), e);
                    commission.markFailed("Token credit failed: " + e.getMessage());
                    referralCommissionRepository.save(commission);
                }
            } else {
                log.debug("Token amount {} below threshold for level {}", tokenAmount, currentLevel);
            }

            // Move up the chain
            currentReferrer = currentReferrer.getReferredBy();
            currentLevel++;
        }

        log.info("Processed {} commissions for payment {}", commissions.size(), payment.getId());
        return commissions;
    }

    /**
     * Check if a beneficiary is qualified to receive commissions.
     *
     * @param beneficiary The user to check
     * @param config The referral configuration
     * @return Null if qualified, or a reason string if disqualified
     */
    private String checkBeneficiaryQualification(User beneficiary, ReferralConfig config) {
        // Check cooling period
        if (config.getCoolingPeriodDays() > 0) {
            LocalDateTime accountAge = beneficiary.getCreatedAt();
            if (accountAge != null) {
                long daysSinceCreation = ChronoUnit.DAYS.between(accountAge, LocalDateTime.now());
                if (daysSinceCreation < config.getCoolingPeriodDays()) {
                    return "Account too new (cooling period: " + config.getCoolingPeriodDays() + " days)";
                }
            }
        }

        // Check active subscription requirement
        if (config.isRequireActiveSubscription()) {
            UserSubscription sub = beneficiary.getCurrentSubscription();
            if (sub == null || sub.getExpiresAt() == null ||
                    sub.getExpiresAt().isBefore(LocalDateTime.now())) {
                return "No active subscription";
            }
        }

        return null; // Qualified
    }

    /**
     * Apply daily and monthly earning caps to the token amount.
     *
     * @param beneficiary The user receiving tokens
     * @param tokenAmount The calculated token amount
     * @param config The referral configuration
     * @return Adjusted token amount after applying caps
     */
    private BigDecimal applyEarningCaps(User beneficiary, BigDecimal tokenAmount, ReferralConfig config) {
        if (tokenAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        // Check daily cap
        if (config.getDailyEarningCap() != null && config.getDailyEarningCap().compareTo(BigDecimal.ZERO) > 0) {
            LocalDateTime startOfDay = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS);
            BigDecimal earnedToday = getEarningsInPeriod(beneficiary, startOfDay, LocalDateTime.now());
            BigDecimal remainingDaily = config.getDailyEarningCap().subtract(earnedToday);

            if (remainingDaily.compareTo(BigDecimal.ZERO) <= 0) {
                log.info("User {} hit daily earning cap of {}", beneficiary.getId(), config.getDailyEarningCap());
                return BigDecimal.ZERO;
            }
            if (tokenAmount.compareTo(remainingDaily) > 0) {
                log.info("Capping user {} earnings to {} (daily cap)", beneficiary.getId(), remainingDaily);
                tokenAmount = remainingDaily;
            }
        }

        // Check monthly cap
        if (config.getMonthlyEarningCap() != null && config.getMonthlyEarningCap().compareTo(BigDecimal.ZERO) > 0) {
            LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
            BigDecimal earnedThisMonth = getEarningsInPeriod(beneficiary, startOfMonth, LocalDateTime.now());
            BigDecimal remainingMonthly = config.getMonthlyEarningCap().subtract(earnedThisMonth);

            if (remainingMonthly.compareTo(BigDecimal.ZERO) <= 0) {
                log.info("User {} hit monthly earning cap of {}", beneficiary.getId(), config.getMonthlyEarningCap());
                return BigDecimal.ZERO;
            }
            if (tokenAmount.compareTo(remainingMonthly) > 0) {
                log.info("Capping user {} earnings to {} (monthly cap)", beneficiary.getId(), remainingMonthly);
                tokenAmount = remainingMonthly;
            }
        }

        return tokenAmount;
    }

    /**
     * Get total credited earnings for a user in a time period.
     */
    private BigDecimal getEarningsInPeriod(User user, LocalDateTime start, LocalDateTime end) {
        return referralCommissionRepository.findByBeneficiaryOrderByCreatedAtDesc(user).stream()
                .filter(c -> c.getStatus() == ReferralCommissionStatus.CREDITED)
                .filter(c -> c.getCreditedAt() != null &&
                        !c.getCreditedAt().isBefore(start) &&
                        !c.getCreditedAt().isAfter(end))
                .map(ReferralCommission::getTokenAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Get the current referral configuration.
     */
    public ReferralConfig getConfig() {
        return configRepository.getConfig();
    }

    /**
     * Credit a pending commission to the beneficiary's token balance.
     *
     * @param commission The commission to credit
     */
    @Transactional
    public void creditCommission(ReferralCommission commission) {
        if (commission.getStatus() != ReferralCommissionStatus.PENDING) {
            log.warn("Commission {} is not in PENDING status, cannot credit", commission.getId());
            return;
        }

        User beneficiary = commission.getBeneficiary();
        BigDecimal tokenAmount = commission.getTokenAmount();

        // Add tokens to beneficiary's balance
        TokenBalance newBalance = tokenService.addTokens(
                beneficiary.getId(),
                tokenAmount,
                TokenTransactionType.REFERRAL
        );

        // Mark commission as credited
        // Note: We don't have the transaction ID directly, but we can mark it as credited
        commission.markCredited(null);
        referralCommissionRepository.save(commission);

        log.info("Credited {} tokens to user {} for commission {} (new balance: {})",
                tokenAmount, beneficiary.getId(), commission.getId(), newBalance.getBalance());
    }

    /**
     * Get the referral chain for a user (upward - who referred them).
     *
     * @param user The user to get the chain for
     * @param maxDepth Maximum depth to traverse
     * @return List of referrers in chain order (immediate referrer first)
     */
    public List<User> getReferralChain(User user, int maxDepth) {
        List<User> chain = new ArrayList<>();
        User current = user.getReferredBy();
        int depth = 0;

        while (current != null && depth < maxDepth) {
            chain.add(current);
            current = current.getReferredBy();
            depth++;
        }

        return chain;
    }

    /**
     * Get the downline network for a user (people they referred).
     *
     * @param user The user whose network to get
     * @param maxLevels Maximum levels to traverse
     * @return Map of level -> list of users at that level
     */
    public java.util.Map<Integer, List<User>> getDownlineNetwork(User user, int maxLevels) {
        java.util.Map<Integer, List<User>> network = new java.util.LinkedHashMap<>();

        // Level 1: Direct referrals
        List<User> directReferrals = userRepository.findByReferredById(user.getId());
        if (!directReferrals.isEmpty()) {
            network.put(1, directReferrals);
        }

        // Level 2+: Indirect referrals
        List<User> currentLevel = directReferrals;
        for (int level = 2; level <= maxLevels && !currentLevel.isEmpty(); level++) {
            List<User> nextLevel = new ArrayList<>();
            for (User referred : currentLevel) {
                nextLevel.addAll(userRepository.findByReferredById(referred.getId()));
            }
            if (!nextLevel.isEmpty()) {
                network.put(level, nextLevel);
            }
            currentLevel = nextLevel;
        }

        return network;
    }

    /**
     * Get total commission earned from a specific user.
     *
     * @param beneficiary The user who earned the commission
     * @param sourceUser The user whose payments generated the commission
     * @return Total tokens earned
     */
    public BigDecimal getTotalCommissionFromUser(User beneficiary, User sourceUser) {
        return referralCommissionRepository.findByBeneficiaryOrderByCreatedAtDesc(beneficiary).stream()
                .filter(c -> c.getSourceUser().getId() == sourceUser.getId())
                .filter(c -> c.getStatus() == ReferralCommissionStatus.CREDITED)
                .map(ReferralCommission::getTokenAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Inject UserRepository
    @org.springframework.beans.factory.annotation.Autowired
    private com.orbvpn.api.repository.UserRepository userRepository;

    /**
     * Get earnings summary for a user.
     *
     * @param user The user
     * @return Summary of earnings by level
     */
    public ReferralEarningsSummary getEarningsSummary(User user) {
        BigDecimal totalEarned = referralCommissionRepository.getTotalTokensEarnedByUser(user);
        BigDecimal pendingTokens = referralCommissionRepository.getTotalPendingTokens(user);
        long directReferrals = referralCommissionRepository.countDirectReferrals(user);
        long totalReferrals = referralCommissionRepository.countTotalReferrals(user);

        List<Object[]> levelStats = referralCommissionRepository.getEarningsSummaryByLevel(user);
        List<LevelEarning> levelEarnings = new ArrayList<>();

        for (Object[] stat : levelStats) {
            int level = (Integer) stat[0];
            long count = (Long) stat[1];
            BigDecimal tokens = (BigDecimal) stat[2];
            levelEarnings.add(new LevelEarning(level, count, tokens));
        }

        return ReferralEarningsSummary.builder()
                .totalTokensEarned(totalEarned)
                .pendingTokens(pendingTokens)
                .directReferrals(directReferrals)
                .totalReferrals(totalReferrals)
                .levelEarnings(levelEarnings)
                .build();
    }

    /**
     * Cancel commissions for a refunded payment.
     *
     * @param paymentId The refunded payment ID
     */
    @Transactional
    public void cancelCommissionsForPayment(Long paymentId) {
        List<ReferralCommission> commissions = referralCommissionRepository.findByPaymentId(paymentId);

        for (ReferralCommission commission : commissions) {
            if (commission.getStatus() == ReferralCommissionStatus.PENDING) {
                commission.setStatus(ReferralCommissionStatus.CANCELLED);
                commission.setNotes("Payment refunded");
                referralCommissionRepository.save(commission);
                log.info("Cancelled pending commission {} due to payment refund", commission.getId());
            } else if (commission.getStatus() == ReferralCommissionStatus.CREDITED) {
                // For credited commissions, we might need to deduct the tokens
                // This is a business decision - for now we just log it
                log.warn("Commission {} was already credited - manual intervention may be needed for refund",
                        commission.getId());
                commission.setNotes("Payment refunded - tokens already credited");
                referralCommissionRepository.save(commission);
            }
        }
    }

    /**
     * Data class for level earnings.
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class LevelEarning {
        private int level;
        private long commissionCount;
        private BigDecimal tokensEarned;
    }

    /**
     * Summary of a user's referral earnings.
     */
    @lombok.Data
    @lombok.Builder
    public static class ReferralEarningsSummary {
        private BigDecimal totalTokensEarned;
        private BigDecimal pendingTokens;
        private long directReferrals;
        private long totalReferrals;
        private List<LevelEarning> levelEarnings;
    }
}
