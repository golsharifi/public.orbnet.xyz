package com.orbvpn.api.service.mining;

import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class RewardCalculationService {
        private final ServerMetricsRepository serverMetricsRepository;
        private final MiningActivityRepository miningActivityRepository;
        private final MiningRewardRepository miningRewardRepository;
        private final TokenBalanceRepository tokenBalanceRepository;

        // Base reward rates
        private static final BigDecimal BASE_RATE_PER_HOUR = new BigDecimal("0.1");
        private static final BigDecimal MAX_RATE_PER_HOUR = new BigDecimal("0.5");
        private static final BigDecimal DATA_TRANSFER_MULTIPLIER = new BigDecimal("0.05"); // per GB

        @Transactional
        public BigDecimal calculateAndDistributeReward(User user, MiningServer server) {
                MiningActivity activity = miningActivityRepository
                                .findByUserAndServerAndIsActiveTrue(user, server)
                                .orElseThrow(() -> new IllegalStateException("No active mining session found"));

                ServerMetrics currentMetrics = serverMetricsRepository
                                .findFirstByServerOrderByLastCheckDesc(server);

                validateInputs(user, server, activity, currentMetrics);

                BigDecimal reward = calculateReward(activity, currentMetrics);
                Duration miningDuration = Duration.between(
                                activity.getStartTime(),
                                activity.getEndTime() != null ? activity.getEndTime() : LocalDateTime.now());

                logRewardCalculation(user, server, reward, miningDuration);
                distributeReward(user, server, reward);

                return reward;
        }

        private BigDecimal calculateReward(MiningActivity activity, ServerMetrics metrics) {
                BigDecimal baseReward = calculateBaseReward(activity);
                BigDecimal serverQualityMultiplier = calculateServerQualityMultiplier(metrics);
                BigDecimal performanceMultiplier = calculatePerformanceMultiplier(activity);
                BigDecimal dataTransferBonus = calculateDataTransferBonus(activity);

                BigDecimal totalReward = baseReward
                                .multiply(serverQualityMultiplier)
                                .multiply(performanceMultiplier)
                                .add(dataTransferBonus)
                                .setScale(8, RoundingMode.HALF_DOWN);

                // Cap the reward rate per hour
                Duration duration = Duration.between(
                                activity.getStartTime(),
                                activity.getEndTime() != null ? activity.getEndTime() : LocalDateTime.now());
                BigDecimal hours = BigDecimal.valueOf(duration.toMinutes())
                                .divide(BigDecimal.valueOf(60), 8, RoundingMode.HALF_DOWN);

                BigDecimal maxReward = MAX_RATE_PER_HOUR.multiply(hours);
                return totalReward.min(maxReward);
        }

        private BigDecimal calculateBaseReward(MiningActivity activity) {
                Duration duration = Duration.between(
                                activity.getStartTime(),
                                activity.getEndTime() != null ? activity.getEndTime() : LocalDateTime.now());

                BigDecimal hours = BigDecimal.valueOf(duration.toMinutes())
                                .divide(BigDecimal.valueOf(60), 8, RoundingMode.HALF_DOWN);

                return BASE_RATE_PER_HOUR.multiply(hours);
        }

        private BigDecimal calculateServerQualityMultiplier(ServerMetrics metrics) {
                // Calculate based on server performance metrics
                BigDecimal uptimeScore = metrics.getUptime()
                                .divide(new BigDecimal("100"), 4, RoundingMode.HALF_DOWN);

                BigDecimal loadScore = BigDecimal.ONE.subtract(
                                BigDecimal.valueOf(metrics.getActiveConnections())
                                                .divide(BigDecimal.valueOf(metrics.getMaxConnections()), 4,
                                                                RoundingMode.HALF_DOWN));

                BigDecimal speedScore = calculateSpeedScore(metrics);

                return uptimeScore
                                .add(loadScore)
                                .add(speedScore)
                                .divide(new BigDecimal("3"), 4, RoundingMode.HALF_DOWN);
        }

        private void logRewardCalculation(User user, MiningServer server, BigDecimal reward, Duration duration) {
                BigDecimal hourlyRate = calculateHourlyRate(reward, duration);
                log.info("Reward calculation - User: {}, Server: {}, Amount: {}, Duration: {}h, Rate: {}/h",
                                user.getId(), server.getId(), reward,
                                duration.toMinutes() / 60.0, hourlyRate);
        }

        private BigDecimal calculateHourlyRate(BigDecimal reward, Duration duration) {
                if (duration.toMinutes() == 0)
                        return BigDecimal.ZERO;
                BigDecimal hours = BigDecimal.valueOf(duration.toMinutes())
                                .divide(BigDecimal.valueOf(60), 8, RoundingMode.HALF_DOWN);
                return reward.divide(hours, 8, RoundingMode.HALF_DOWN);
        }

        private BigDecimal calculateSpeedScore(ServerMetrics metrics) {
                // Normalize speed scores (assuming 1000 Mbps as baseline)
                BigDecimal uploadScore = metrics.getUploadSpeed()
                                .divide(new BigDecimal("1000"), 4, RoundingMode.HALF_DOWN);
                BigDecimal downloadScore = metrics.getDownloadSpeed()
                                .divide(new BigDecimal("1000"), 4, RoundingMode.HALF_DOWN);

                return uploadScore.add(downloadScore)
                                .divide(new BigDecimal("2"), 4, RoundingMode.HALF_DOWN);
        }

        private BigDecimal calculatePerformanceMultiplier(MiningActivity activity) {
                return activity.getConnectionStability()
                                .multiply(activity.getProtocolEfficiency())
                                .add(BigDecimal.ONE)
                                .divide(new BigDecimal("2"), 4, RoundingMode.HALF_DOWN);
        }

        private BigDecimal calculateDataTransferBonus(MiningActivity activity) {
                return activity.getDataTransferred()
                                .multiply(DATA_TRANSFER_MULTIPLIER);
        }

        @Transactional
        private void distributeReward(User user, MiningServer server, BigDecimal amount) {
                // Update token balance
                TokenBalance balance = tokenBalanceRepository.findByUser(user)
                                .orElseGet(() -> TokenBalance.builder()
                                                .user(user)
                                                .balance(BigDecimal.ZERO)
                                                .build());

                balance.setBalance(balance.getBalance().add(amount));
                balance.setLastActivityDate(LocalDateTime.now());
                tokenBalanceRepository.save(balance);

                // Record reward
                MiningReward reward = MiningReward.builder()
                                .user(user)
                                .server(server)
                                .amount(amount)
                                .rewardTime(LocalDateTime.now())
                                .build();
                miningRewardRepository.save(reward);

                log.info("Distributed {} tokens to user {} for mining on server {}",
                                amount, user.getId(), server.getId());
        }

        private void validateInputs(User user, MiningServer server, MiningActivity activity, ServerMetrics metrics) {
                if (user == null)
                        throw new IllegalArgumentException("User cannot be null");
                if (server == null)
                        throw new IllegalArgumentException("Server cannot be null");
                if (activity == null)
                        throw new IllegalArgumentException("Activity cannot be null");
                if (metrics == null)
                        throw new IllegalArgumentException("Metrics cannot be null");
                if (activity.getStartTime() == null)
                        throw new IllegalArgumentException("Start time cannot be null");
                if (activity.getStartTime().isAfter(LocalDateTime.now())) {
                        throw new IllegalArgumentException("Start time cannot be in the future");
                }
        }

}
