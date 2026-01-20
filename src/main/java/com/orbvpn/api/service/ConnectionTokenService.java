package com.orbvpn.api.service;

import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.repository.ConnectionStatsRepository;
import com.orbvpn.api.repository.TokenBalanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConnectionTokenService {
    private final ConnectionStatsRepository connectionStatsRepository;
    private final TokenBalanceRepository tokenBalanceRepository;

    private static final BigDecimal TOKEN_COST_PER_GB = new BigDecimal("0.1");
    private static final BigDecimal BASE_REWARD_RATE = new BigDecimal("0.05");

    @Transactional
    public void updateTokensForConnection(ConnectionStats stats) {
        if (stats.getConnectionEnd() == null) {
            return; // Only process completed connections
        }

        // Calculate tokens cost for user
        BigDecimal tokensCost = calculateTokensCost(stats);
        stats.setTokensCost(tokensCost);

        // Calculate tokens earned for server operator
        BigDecimal tokensEarned = calculateTokensEarned(stats);
        stats.setTokensEarned(tokensEarned);

        // Update token balances
        updateUserTokenBalance(stats.getUser(), tokensCost.negate());
        updateUserTokenBalance(stats.getServer().getOperator(), tokensEarned);

        connectionStatsRepository.save(stats);
    }

    private BigDecimal calculateTokensCost(ConnectionStats stats) {
        return stats.getDataTransferred()
                .multiply(TOKEN_COST_PER_GB)
                .setScale(8, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateTokensEarned(ConnectionStats stats) {
        Duration duration = Duration.between(stats.getConnectionStart(), stats.getConnectionEnd());
        BigDecimal hours = BigDecimal.valueOf(duration.toMinutes()).divide(BigDecimal.valueOf(60), 8,
                RoundingMode.HALF_UP);

        return BASE_REWARD_RATE
                .multiply(hours)
                .multiply(calculatePerformanceMultiplier(stats))
                .setScale(8, RoundingMode.HALF_UP);
    }

    private BigDecimal calculatePerformanceMultiplier(ConnectionStats stats) {
        BigDecimal cpuMultiplier = BigDecimal.ONE.subtract(
                BigDecimal.valueOf(stats.getCpuUsage()).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));

        BigDecimal memoryMultiplier = BigDecimal.ONE.subtract(
                BigDecimal.valueOf(stats.getMemoryUsage()).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));

        BigDecimal networkMultiplier = BigDecimal.valueOf(stats.getNetworkSpeed())
                .divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP)
                .min(BigDecimal.ONE);

        return cpuMultiplier
                .add(memoryMultiplier)
                .add(networkMultiplier)
                .divide(BigDecimal.valueOf(3), 4, RoundingMode.HALF_UP);
    }

    private void updateUserTokenBalance(User user, BigDecimal amount) {
        TokenBalance balance = tokenBalanceRepository.findByUser(user)
                .orElseGet(() -> TokenBalance.builder()
                        .user(user)
                        .balance(BigDecimal.ZERO)
                        .build());

        balance.setBalance(balance.getBalance().add(amount));
        tokenBalanceRepository.save(balance);
    }
}