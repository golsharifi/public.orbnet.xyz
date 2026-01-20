package com.orbvpn.api.service;

import com.orbvpn.api.domain.dto.StakingStats;
import com.orbvpn.api.domain.dto.TokenStakingConfigInput;
import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.domain.enums.TokenTransactionType;
import com.orbvpn.api.exception.*;
import com.orbvpn.api.repository.TokenStakeRepository;
import com.orbvpn.api.repository.TokenStakingConfigRepository;
import com.orbvpn.api.service.subscription.UserSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TokenStakingService {
    private final TokenStakingConfigRepository stakingConfigRepository;
    private final TokenStakeRepository stakeRepository;
    private final AdTokenServiceImpl AdTokenService;
    private final UserService userService;

    @Autowired
    private UserSubscriptionService userSubscriptionService;

    // Configuration Management Methods
    @Transactional
    public TokenStakingConfig createConfig(TokenStakingConfigInput input) {
        validateStakingConfig(input);

        TokenStakingConfig config = new TokenStakingConfig();
        updateConfigFromInput(config, input);
        return stakingConfigRepository.save(config);
    }

    @Transactional
    public TokenStakingConfig updateConfig(Long id, TokenStakingConfigInput input) {
        validateStakingConfig(input);

        TokenStakingConfig config = stakingConfigRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Staking configuration not found"));

        updateConfigFromInput(config, input);
        return stakingConfigRepository.save(config);
    }

    private void updateConfigFromInput(TokenStakingConfig config, TokenStakingConfigInput input) {
        config.setName(input.getName());
        config.setBaseApy(input.getBaseApy());
        config.setBonusApyPerMonth(input.getBonusApyPerMonth());
        config.setMinimumLockDays(input.getMinimumLockDays());
        config.setMaximumLockDays(input.getMaximumLockDays());
        config.setMinimumStakeAmount(input.getMinimumStakeAmount());
        config.setMaximumStakeAmount(input.getMaximumStakeAmount());
        config.setIsActive(input.getIsActive() != null ? input.getIsActive() : true);

        // Clear existing requirements
        config.getRequirements().clear();

        // Add new requirements if any exist
        if (input.getRequirements() != null) {
            List<TokenStakingTierRequirement> newRequirements = input.getRequirements().stream()
                    .map(reqInput -> {
                        TokenStakingTierRequirement requirement = new TokenStakingTierRequirement();
                        requirement.setStakingConfig(config);
                        requirement.setRequirementType(reqInput.getRequirementType());
                        requirement.setRequirementValue(reqInput.getRequirementValue());
                        return requirement;
                    })
                    .collect(Collectors.toList());

            config.getRequirements().addAll(newRequirements);
        }
    }

    private void validateStakingConfig(TokenStakingConfigInput input) {
        if (input.getMinimumLockDays() > input.getMaximumLockDays()) {
            throw new ValidationException("Minimum lock days cannot be greater than maximum lock days");
        }
        if (input.getMaximumStakeAmount() != null &&
                input.getMinimumStakeAmount().compareTo(input.getMaximumStakeAmount()) > 0) {
            throw new ValidationException("Minimum stake amount cannot be greater than maximum stake amount");
        }
    }

    @Transactional
    public Boolean deleteConfig(Long id) {
        stakingConfigRepository.deleteById(id);
        return true;
    }

    @Transactional
    public TokenStakingConfig toggleConfig(Long id) {
        log.info("Toggling staking config status for id: {}", id);

        TokenStakingConfig config = stakingConfigRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Staking configuration not found with id: {}", id);
                    return new NotFoundException("Staking configuration not found with id: " + id);
                });

        boolean currentStatus = Boolean.TRUE.equals(config.getIsActive());
        config.setIsActive(!currentStatus);

        TokenStakingConfig savedConfig = stakingConfigRepository.save(config);
        log.info("Successfully toggled staking config {} status from {} to {}",
                id, currentStatus, savedConfig.getIsActive());

        return savedConfig;
    }

    // User Staking Methods
    @Transactional
    public TokenStake stakeTokens(BigDecimal amount, Integer lockPeriodDays) {
        User user = userService.getUser();

        // Validate stake amount and period
        TokenStakingConfig config = getApplicableStakingConfig(user, amount, lockPeriodDays);
        validateStakingRequirements(user, config);

        // Verify user has enough tokens
        TokenBalance balance = AdTokenService.getBalance(user.getId());
        if (balance.getBalance().compareTo(amount) < 0) {
            throw new InsufficientTokensException("Insufficient tokens for staking");
        }

        // Calculate APY
        BigDecimal monthsLocked = BigDecimal.valueOf(lockPeriodDays)
                .divide(BigDecimal.valueOf(30), 6, RoundingMode.HALF_UP);
        BigDecimal totalApy = config.getBaseApy()
                .add(config.getBonusApyPerMonth().multiply(monthsLocked));

        // Create stake record
        TokenStake stake = new TokenStake();
        stake.setUser(user);
        stake.setAmount(amount);
        stake.setStakedAt(LocalDateTime.now());
        stake.setLockPeriodDays(lockPeriodDays);
        stake.setRewardRate(totalApy);
        stake.setStakingConfig(config);

        // Deduct tokens from user's balance
        AdTokenService.deductTokens(user.getId(), amount, TokenTransactionType.STAKE);

        return stakeRepository.save(stake);
    }

    @Transactional
    public TokenStake unstakeTokens(Long stakeId) {
        User user = userService.getUser();
        TokenStake stake = stakeRepository.findById(stakeId)
                .orElseThrow(() -> new NotFoundException("Stake not found"));

        if (stake.getUser().getId() != user.getId()) {
            throw new UnauthorizedException("Not authorized to unstake these tokens");
        }

        // Verify lock period has ended
        if (stake.getStakedAt().plusDays(stake.getLockPeriodDays()).isAfter(LocalDateTime.now())) {
            throw new ValidationException("Tokens are still locked");
        }

        if (stake.getUnstakedAt() != null) {
            throw new ValidationException("Tokens are already unstaked");
        }

        // Calculate rewards
        BigDecimal rewards = calculateRewards(stake);
        BigDecimal totalAmount = stake.getAmount().add(rewards);

        // Mark as unstaked
        stake.setUnstakedAt(LocalDateTime.now());
        stakeRepository.save(stake);

        // Return tokens + rewards to user
        AdTokenService.addTokens(user.getId(), totalAmount, TokenTransactionType.UNSTAKE);

        return stake;
    }

    // Reward Distribution
    @Scheduled(cron = "0 0 0 * * *") // Run daily at midnight
    @Transactional
    public void distributeStakingRewards() {
        log.info("Starting daily staking rewards distribution");
        List<TokenStake> activeStakes = stakeRepository.findByUnstakedAtIsNull();

        for (TokenStake stake : activeStakes) {
            try {
                BigDecimal dailyReward = calculateDailyReward(stake);
                AdTokenService.addTokens(
                        stake.getUser().getId(),
                        dailyReward,
                        TokenTransactionType.STAKE_REWARD);

                log.info("Distributed {} tokens as staking reward to user {}",
                        dailyReward, stake.getUser().getId());
            } catch (Exception e) {
                log.error("Failed to distribute staking reward for stake {}", stake.getId(), e);
            }
        }
    }

    // Helper Methods
    private TokenStakingConfig getApplicableStakingConfig(User user, BigDecimal amount, Integer lockPeriodDays) {
        return stakingConfigRepository.findByIsActiveTrue().stream()
                .filter(config -> lockPeriodDays >= config.getMinimumLockDays() &&
                        lockPeriodDays <= config.getMaximumLockDays() &&
                        amount.compareTo(config.getMinimumStakeAmount()) >= 0 &&
                        (config.getMaximumStakeAmount() == null ||
                                amount.compareTo(config.getMaximumStakeAmount()) <= 0))
                .findFirst()
                .orElseThrow(() -> new ValidationException("No applicable staking tier found"));
    }

    private void validateStakingRequirements(User user, TokenStakingConfig config) {
        for (TokenStakingTierRequirement req : config.getRequirements()) {
            switch (req.getRequirementType()) {
                case "SUBSCRIPTION_TYPE":
                    validateSubscriptionRequirement(user, req.getRequirementValue());
                    break;
                case "TOTAL_TOKENS":
                    validateTokenBalanceRequirement(user, req.getRequirementValue());
                    break;
                case "USER_LEVEL":
                    validateUserLevelRequirement(user, req.getRequirementValue());
                    break;
                default:
                    throw new ValidationException("Unknown requirement type: " + req.getRequirementType());
            }
        }
    }

    private void validateSubscriptionRequirement(User user, String requiredType) {
        UserSubscription subscription = userSubscriptionService.getCurrentSubscription(user);
        if (subscription == null || !subscription.getGroup().getName().equals(requiredType)) {
            throw new ValidationException("Required subscription type: " + requiredType);
        }
    }

    private void validateTokenBalanceRequirement(User user, String requiredAmount) {
        TokenBalance balance = AdTokenService.getBalance(user.getId());
        if (balance.getBalance().compareTo(new BigDecimal(requiredAmount)) < 0) {
            throw new ValidationException("Required minimum token balance: " + requiredAmount);
        }
    }

    private void validateUserLevelRequirement(User user, String requiredLevel) {
        // Implement based on your user level system
    }

    private BigDecimal calculateDailyReward(TokenStake stake) {
        return stake.getAmount()
                .multiply(stake.getRewardRate())
                .divide(BigDecimal.valueOf(365), 6, RoundingMode.HALF_DOWN);
    }

    private BigDecimal calculateRewards(TokenStake stake) {
        long daysStaked = ChronoUnit.DAYS.between(stake.getStakedAt(), LocalDateTime.now());
        return stake.getAmount()
                .multiply(stake.getRewardRate())
                .multiply(BigDecimal.valueOf(daysStaked))
                .divide(BigDecimal.valueOf(365), 6, RoundingMode.HALF_DOWN);
    }

    // Query Methods
    public List<TokenStakingConfig> getAllConfigs() {
        return stakingConfigRepository.findAll();
    }

    public TokenStakingConfig getConfig(Long id) {
        return stakingConfigRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Staking configuration not found"));
    }

    public List<TokenStakingConfig> getAvailableStakingOptions() {
        User user = userService.getUser();
        return stakingConfigRepository.findByIsActiveTrue().stream()
                .filter(config -> meetsRequirements(user, config))
                .collect(Collectors.toList());
    }

    private boolean meetsRequirements(User user, TokenStakingConfig config) {
        try {
            validateStakingRequirements(user, config);
            return true;
        } catch (ValidationException e) {
            return false;
        }
    }

    public List<TokenStake> getCurrentUserActiveStakes() {
        User user = userService.getUser();
        return stakeRepository.findByUserIdAndUnstakedAtIsNull(user.getId());
    }

    // Statistics Methods
    public StakingStats getGlobalStats() {
        List<TokenStake> activeStakes = stakeRepository.findByUnstakedAtIsNull();

        BigDecimal totalStaked = activeStakes.stream()
                .map(TokenStake::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalRewards = activeStakes.stream()
                .map(this::calculateRewards)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgApy = activeStakes.isEmpty() ? BigDecimal.ZERO
                : activeStakes.stream()
                        .map(TokenStake::getRewardRate)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(activeStakes.size()), 6, RoundingMode.HALF_UP);

        return StakingStats.builder()
                .totalStaked(totalStaked)
                .totalRewardsEarned(totalRewards)
                .activeStakes(activeStakes.size())
                .averageApy(avgApy)
                .build();
    }

    public StakingStats getUserStats(int userId) {
        List<TokenStake> userStakes = stakeRepository.findByUserIdAndUnstakedAtIsNull(userId);

        BigDecimal totalStaked = userStakes.stream()
                .map(TokenStake::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalRewards = userStakes.stream()
                .map(this::calculateRewards)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgApy = userStakes.isEmpty() ? BigDecimal.ZERO
                : userStakes.stream()
                        .map(TokenStake::getRewardRate)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(userStakes.size()), 6, RoundingMode.HALF_UP);

        return StakingStats.builder()
                .totalStaked(totalStaked)
                .totalRewardsEarned(totalRewards)
                .activeStakes(userStakes.size())
                .averageApy(avgApy)
                .build();
    }

    public StakingStats getCurrentUserStats() {
        return getUserStats(userService.getUser().getId());
    }
}