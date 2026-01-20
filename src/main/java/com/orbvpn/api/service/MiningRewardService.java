package com.orbvpn.api.service;

import com.orbvpn.api.domain.dto.*;
import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.exception.*;
import com.orbvpn.api.repository.*;
import com.orbvpn.api.service.blockchain.BlockchainService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MiningRewardService {
    private final MiningServerRepository miningServerRepository;
    private final TokenBalanceRepository tokenBalanceRepository;
    private final MiningRewardRepository miningRewardRepository;
    private final BlockchainService blockchainService;
    private final MiningActivityRepository miningActivityRepository;
    private final MiningSettingsRepository miningSettingsRepository;

    @Transactional
    public MiningRewardResult claimRewards(User user) {
        List<MiningActivity> activities = miningActivityRepository
                .findByUserAndIsActiveTrue(user);

        BigDecimal totalRewards = activities.stream()
                .map(activity -> calculateRewards(activity.getServer(), user))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalRewards.compareTo(BigDecimal.ZERO) <= 0) {
            return MiningRewardResult.builder()
                    .success(false)
                    .amount(BigDecimal.ZERO)
                    .newBalance(BigDecimal.ZERO)
                    .message("No rewards available to claim")
                    .build();
        }

        TokenBalance balance = updateBalance(user, totalRewards);

        activities.forEach(activity -> {
            activity.setRewardEarned(calculateRewards(activity.getServer(), user));
            miningActivityRepository.save(activity);
        });

        return MiningRewardResult.builder()
                .success(true)
                .amount(totalRewards)
                .newBalance(balance.getBalance())
                .message("Rewards claimed successfully")
                .build();
    }

    private TokenBalance updateBalance(User user, BigDecimal amount) {
        TokenBalance balance = tokenBalanceRepository.findByUser(user)
                .orElse(TokenBalance.builder()
                        .user(user)
                        .balance(BigDecimal.ZERO)
                        .build());

        balance.setBalance(balance.getBalance().add(amount));
        return tokenBalanceRepository.save(balance);
    }

    @Transactional
    public MiningRewardResult claimRewards(Long serverId, User user) {
        MiningServer server = miningServerRepository.findById(serverId)
                .orElseThrow(() -> new NotFoundException("Server not found"));

        BigDecimal rewards = calculateRewards(server, user);

        if (rewards.compareTo(BigDecimal.ZERO) <= 0) {
            return MiningRewardResult.builder()
                    .success(false)
                    .amount(BigDecimal.ZERO)
                    .newBalance(BigDecimal.ZERO)
                    .message("No rewards available to claim")
                    .build();
        }

        TokenBalance balance = updateBalance(user, rewards);

        return MiningRewardResult.builder()
                .success(true)
                .amount(rewards)
                .newBalance(balance.getBalance())
                .message("Rewards claimed successfully")
                .build();
    }

    private BigDecimal calculateRewards(MiningServer server, User user) {
        LocalDateTime lastClaim = server.getLastRewardClaim();
        if (lastClaim == null) {
            lastClaim = LocalDateTime.now().minusHours(24); // Max 24h rewards
        }

        Duration miningTime = Duration.between(lastClaim, LocalDateTime.now());
        BigDecimal hours = BigDecimal.valueOf(miningTime.toHours());

        return hours.multiply(server.getMiningRate());
    }

    public MiningStats getMiningStats(User user) {
        List<MiningRewardView> rewardHistory = getRewardHistory(user);
        List<MiningServerView> topServers = getTopMiningServers(user);

        return MiningStats.builder()
                .totalRewards(calculateTotalRewards(user))
                .todayRewards(calculateTodayRewards(user))
                .activeMiningSessions(countActiveMiningSessions(user))
                .averageDailyReward(calculateAverageDailyReward(user))
                .topServers(topServers)
                .rewardHistory(rewardHistory)
                .build();
    }

    @Transactional
    public WithdrawResult withdrawTokens(BigDecimal amount, User user) {
        TokenBalance balance = tokenBalanceRepository.findByUser(user)
                .orElseThrow(() -> new NotFoundException("No token balance found"));

        if (balance.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient token balance");
        }

        MiningSettingsView settings = getUserMiningSettings(user);
        if (amount.compareTo(settings.getMinWithdrawAmount()) < 0) {
            throw new BadRequestException("Amount below minimum withdrawal limit");
        }

        try {
            String txHash = blockchainService.transferTokens(
                    settings.getWithdrawAddress(),
                    amount);

            balance.setBalance(balance.getBalance().subtract(amount));
            tokenBalanceRepository.save(balance);

            return WithdrawResult.builder()
                    .success(true)
                    .transactionHash(txHash)
                    .message("Withdrawal successful")
                    .build();
        } catch (Exception e) {
            log.error("Withdrawal failed", e);
            throw new PaymentException("Withdrawal failed: " + e.getMessage());
        }
    }

    private MiningSettingsView getUserMiningSettings(User user) {
        // Get settings from database or configuration
        MiningSettingsEntity settings = miningSettingsRepository.findByUser(user)
                .orElseThrow(() -> new NotFoundException("Mining settings not found"));

        return MiningSettingsView.builder()
                .id(settings.getId())
                .withdrawAddress(settings.getWithdrawAddress())
                .minWithdrawAmount(settings.getMinWithdrawAmount())
                .notifications(settings.getNotificationsEnabled())
                .autoWithdraw(settings.getAutoWithdraw())
                .build();
    }

    public List<MiningRewardView> getRewards(User user, LocalDateTime from, LocalDateTime to) {
        return miningRewardRepository.findByUserAndRewardTimeBetween(user, from, to)
                .stream()
                .map(this::convertToRewardView)
                .collect(Collectors.toList());
    }

    private MiningRewardView convertToRewardView(MiningReward reward) {
        return MiningRewardView.builder()
                .id(reward.getId())
                .server(convertToServerView(reward.getServer()))
                .amount(reward.getAmount())
                .rewardTime(reward.getRewardTime())
                .transactionHash(reward.getTransactionHash())
                .build();
    }

    private MiningServerView convertToServerView(MiningServer server) {
        return MiningServerView.builder()
                .id(server.getId())
                .hostName(server.getHostName())
                .publicIp(server.getPublicIp())
                .location(server.getLocation())
                .build();
    }

    // Implement these methods
    private BigDecimal calculateTotalRewards(User user) {
        return miningRewardRepository.sumRewardsByUser(user);
    }

    private BigDecimal calculateTodayRewards(User user) {
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        return miningRewardRepository.sumRewardsByUserAndDateAfter(user, startOfDay);
    }

    private int countActiveMiningSessions(User user) {
        return miningRewardRepository.countActiveSessionsByUser(user);
    }

    private BigDecimal calculateAverageDailyReward(User user) {
        return miningRewardRepository.calculateAverageDailyReward(user);
    }

    private List<MiningServerView> getTopMiningServers(User user) {
        return miningRewardRepository.findTopServersByUser(user).stream()
                .map(this::convertToServerView)
                .collect(Collectors.toList());
    }

    private List<MiningRewardView> getRewardHistory(User user) {
        return miningRewardRepository.findByUserOrderByRewardTimeDesc(user).stream()
                .map(this::convertToRewardView)
                .collect(Collectors.toList());
    }
}
