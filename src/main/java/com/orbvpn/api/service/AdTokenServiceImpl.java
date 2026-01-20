package com.orbvpn.api.service;

import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.domain.enums.TokenTransactionType;
import com.orbvpn.api.repository.*;
import com.orbvpn.api.exception.AdLimitExceededException;
import com.orbvpn.api.exception.InsufficientTokensException;
import com.orbvpn.api.exception.NotFoundException;
import com.orbvpn.api.domain.dto.DailyStats;
import com.orbvpn.api.domain.dto.GlobalTokenStats;
import com.orbvpn.api.domain.dto.RemainingLimits;
import com.orbvpn.api.domain.dto.UserTokenStats;
import com.orbvpn.api.service.user.UserContextService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdTokenServiceImpl implements TokenServiceInterface {
    private final TokenBalanceRepository tokenBalanceRepository;
    private final TokenTransactionRepository tokenTransactionRepository;
    private final TokenRateRepository tokenRateRepository;
    private final UserRepository userRepository;
    private final UserContextService userContextService;

    private User getUserById(Integer userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));
    }

    @Override
    @Transactional
    public TokenBalance earnTokens(Integer userId, String adVendor, String region) {
        User user = getUserById(userId);
        TokenRate rate = getTokenRate(region, adVendor);

        validateAdLimits(userId, rate);

        TokenTransaction transaction = new TokenTransaction();
        transaction.setUser(user);
        transaction.setAmount(rate.getTokenPerAd());
        transaction.setType(TokenTransactionType.EARN);
        transaction.setAdVendor(adVendor);
        transaction.setRegion(region);
        tokenTransactionRepository.save(transaction);

        TokenBalance balance = getOrCreateBalance(user);
        balance.setBalance(balance.getBalance().add(rate.getTokenPerAd()));
        balance.setLastActivityDate(LocalDateTime.now());

        return tokenBalanceRepository.save(balance);
    }

    @Override
    @Transactional
    public TokenBalance spendTokens(Integer userId, int minutes, int activeDevices) {
        User user = getUserById(userId);
        TokenBalance balance = getOrCreateBalance(user);
        TokenRate rate = getDefaultTokenRate();

        BigDecimal consumptionRate = calculateConsumptionRate(rate, minutes, activeDevices);

        if (balance.getBalance().compareTo(consumptionRate) < 0) {
            throw new InsufficientTokensException("Insufficient tokens for VPN usage");
        }

        TokenTransaction transaction = new TokenTransaction();
        transaction.setUser(user);
        transaction.setAmount(consumptionRate.negate());
        transaction.setType(TokenTransactionType.SPEND);
        tokenTransactionRepository.save(transaction);

        balance.setBalance(balance.getBalance().subtract(consumptionRate));
        balance.setLastActivityDate(LocalDateTime.now());

        return tokenBalanceRepository.save(balance);
    }

    private BigDecimal calculateConsumptionRate(TokenRate rate, int minutes, int activeDevices) {
        BigDecimal baseRate = rate.getTokenPerMinute().multiply(BigDecimal.valueOf(minutes));
        if (activeDevices > 1) {
            return baseRate.multiply(rate.getMultiDeviceRate().multiply(BigDecimal.valueOf(activeDevices)));
        }
        return baseRate;
    }

    private void validateAdLimits(Integer userId, TokenRate rate) {
        LocalDateTime now = LocalDateTime.now();

        int hourlyAds = tokenTransactionRepository.countAdsWatched(
                userId,
                now.minusHours(1),
                now);

        if (hourlyAds >= rate.getHourlyAdLimit()) {
            throw new AdLimitExceededException(
                    AdLimitExceededException.LimitType.HOURLY,
                    hourlyAds,
                    rate.getHourlyAdLimit());
        }

        int dailyAds = tokenTransactionRepository.countAdsWatched(
                userId,
                now.truncatedTo(ChronoUnit.DAYS),
                now);

        if (dailyAds >= rate.getDailyAdLimit()) {
            throw new AdLimitExceededException(
                    AdLimitExceededException.LimitType.DAILY,
                    dailyAds,
                    rate.getDailyAdLimit());
        }
    }

    @Override
    public boolean hasValidSubscription(Integer userId) {
        User user = getUserById(userId);
        TokenBalance balance = getOrCreateBalance(user);
        TokenRate rate = getDefaultTokenRate();

        int weeklyAds = tokenTransactionRepository.countAdsWatched(
                userId,
                LocalDateTime.now().minusWeeks(1),
                LocalDateTime.now());

        boolean hasMinimumBalance = balance.getBalance().compareTo(rate.getMinimumBalance()) >= 0;

        return weeklyAds >= rate.getMinWeeklyAds() && hasMinimumBalance;
    }

    private TokenBalance getOrCreateBalance(User user) {
        return tokenBalanceRepository.findByUser_Id(user.getId())
                .orElseGet(() -> {
                    TokenBalance newBalance = new TokenBalance();
                    newBalance.setUser(user);
                    newBalance.setBalance(BigDecimal.ZERO);
                    return tokenBalanceRepository.save(newBalance);
                });
    }

    @Override
    public TokenBalance getBalance(Integer userId) {
        User user = getUserById(userId);
        return getOrCreateBalance(user);
    }

    private TokenRate getTokenRate(String region, String adVendor) {
        return tokenRateRepository.findByRegionAndAdVendor(region, adVendor)
                .orElseThrow(() -> new RuntimeException("No token rate configured for region and vendor"));
    }

    private TokenRate getDefaultTokenRate() {
        return tokenRateRepository.findByRegionAndAdVendor("DEFAULT", "DEFAULT")
                .orElseThrow(() -> new RuntimeException("No default token rate configured"));
    }

    @Override
    @Transactional
    public TokenBalance addTokens(Integer userId, BigDecimal amount, TokenTransactionType type) {
        User user = getUserById(userId);
        TokenBalance balance = getOrCreateBalance(user);

        TokenTransaction transaction = new TokenTransaction();
        transaction.setUser(user);
        transaction.setAmount(amount);
        transaction.setType(type);
        tokenTransactionRepository.save(transaction);

        balance.setBalance(balance.getBalance().add(amount));
        balance.setLastActivityDate(LocalDateTime.now());

        return tokenBalanceRepository.save(balance);
    }

    @Override
    @Transactional
    public TokenBalance deductTokens(Integer userId, BigDecimal amount, TokenTransactionType type) {
        User user = getUserById(userId);
        TokenBalance balance = getOrCreateBalance(user);

        if (balance.getBalance().compareTo(amount) < 0) {
            throw new InsufficientTokensException("Insufficient tokens for deduction");
        }

        TokenTransaction transaction = new TokenTransaction();
        transaction.setUser(user);
        transaction.setAmount(amount.negate());
        transaction.setType(type);
        tokenTransactionRepository.save(transaction);

        balance.setBalance(balance.getBalance().subtract(amount));
        balance.setLastActivityDate(LocalDateTime.now());

        return tokenBalanceRepository.save(balance);
    }

    @Override
    @Transactional
    public boolean hasDailySubscription(Integer userId) {
        TokenRate rate = getDefaultTokenRate();

        int dailyAds = tokenTransactionRepository.countAdsWatched(
                userId,
                LocalDateTime.now().truncatedTo(ChronoUnit.DAYS),
                LocalDateTime.now());

        return dailyAds >= rate.getMinDailyAds();
    }

    @Transactional
    public TokenRate updateTokenRate(String region, String adVendor, BigDecimal tokenPerAd, BigDecimal tokenPerMinute,
            Integer dailyAdLimit, Integer hourlyAdLimit, Integer minDailyAds, Integer minWeeklyAds,
            Integer deviceLimit, BigDecimal multiDeviceRate) {
        TokenRate rate = tokenRateRepository.findByRegionAndAdVendor(region, adVendor)
                .orElse(new TokenRate());

        rate.setRegion(region);
        rate.setAdVendor(adVendor);
        rate.setTokenPerAd(tokenPerAd);
        rate.setTokenPerMinute(tokenPerMinute);
        rate.setDailyAdLimit(dailyAdLimit);
        rate.setHourlyAdLimit(hourlyAdLimit);
        rate.setMinDailyAds(minDailyAds);
        rate.setMinWeeklyAds(minWeeklyAds);
        rate.setDeviceLimit(deviceLimit);
        rate.setMultiDeviceRate(multiDeviceRate);

        return tokenRateRepository.save(rate);
    }

    @Transactional
    public boolean deleteTokenRateById(Long id) {
        if (tokenRateRepository.existsById(id)) {
            tokenRateRepository.deleteById(id);
            return true;
        } else {
            return false;
        }
    }

    @Transactional
    public boolean deleteTokenRate(String region, String adVendor) {
        return tokenRateRepository.deleteByRegionAndAdVendor(region, adVendor) > 0;
    }

    @Override
    @Transactional
    public DailyStats getDailyStats(Integer userId) {
        TokenRate rate = getDefaultTokenRate();

        LocalDateTime startOfDay = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS);
        LocalDateTime now = LocalDateTime.now();

        int watchedAds = tokenTransactionRepository.countAdsWatched(userId, startOfDay, now);

        BigDecimal earned = tokenTransactionRepository.sumAmountByTypeAndDateRange(
                userId,
                TokenTransactionType.EARN,
                startOfDay,
                now);

        BigDecimal spent = tokenTransactionRepository.sumAmountByTypeAndDateRange(
                userId,
                TokenTransactionType.SPEND,
                startOfDay,
                now);

        return DailyStats.builder()
                .watchedAds(watchedAds)
                .remainingAds(rate.getDailyAdLimit() - watchedAds)
                .tokensEarned(earned != null ? earned : BigDecimal.ZERO)
                .tokensSpent(spent != null ? spent.abs() : BigDecimal.ZERO)
                .build();
    }

    @Override
    @Transactional
    public RemainingLimits getRemainingLimits(Integer userId) {
        TokenRate rate = getDefaultTokenRate();
        LocalDateTime now = LocalDateTime.now();

        int dailyAds = tokenTransactionRepository.countAdsWatched(
                userId,
                now.truncatedTo(ChronoUnit.DAYS),
                now);

        int hourlyAds = tokenTransactionRepository.countAdsWatched(
                userId,
                now.truncatedTo(ChronoUnit.HOURS),
                now);

        return RemainingLimits.builder()
                .remainingDailyAds(rate.getDailyAdLimit() - dailyAds)
                .remainingHourlyAds(rate.getHourlyAdLimit() - hourlyAds)
                .nextHourlyReset(now.truncatedTo(ChronoUnit.HOURS).plusHours(1))
                .nextDailyReset(now.truncatedTo(ChronoUnit.DAYS).plusDays(1))
                .build();
    }

    @Override
    @Transactional
    public GlobalTokenStats getGlobalStats() {
        LocalDateTime startOfDay = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS);
        LocalDateTime now = LocalDateTime.now();

        List<TokenBalance> allBalances = tokenBalanceRepository.findAll();
        BigDecimal totalTokens = allBalances.stream()
                .map(TokenBalance::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long activeUsers = tokenTransactionRepository.countDistinctUsersByDateRange(
                startOfDay, now);

        BigDecimal totalEarned = tokenTransactionRepository.sumTotalAmountByTypeAndDateRange(
                TokenTransactionType.EARN, startOfDay, now);

        BigDecimal totalSpent = tokenTransactionRepository.sumTotalAmountByTypeAndDateRange(
                TokenTransactionType.SPEND, startOfDay, now);

        int totalAdsWatched = tokenTransactionRepository.countTotalAdsWatched(startOfDay, now);

        return GlobalTokenStats.builder()
                .totalActiveUsers(allBalances.size())
                .totalDailyActiveUsers(activeUsers)
                .totalTokensInCirculation(totalTokens)
                .totalTokensEarnedToday(totalEarned != null ? totalEarned : BigDecimal.ZERO)
                .totalTokensSpentToday(totalSpent != null ? totalSpent.abs() : BigDecimal.ZERO)
                .totalAdsWatchedToday(totalAdsWatched)
                .averageTokensPerUser(totalTokens.divide(
                        BigDecimal.valueOf(Math.max(1, allBalances.size())),
                        2,
                        RoundingMode.HALF_UP).doubleValue())
                .averageTokensEarnedPerUser(
                        totalEarned != null ? totalEarned.divide(BigDecimal.valueOf(Math.max(1, activeUsers)),
                                2,
                                RoundingMode.HALF_UP).doubleValue() : 0.0)
                .build();
    }

    @Override
    @Transactional
    public List<UserTokenStats> getAllUserStats(Integer page, Integer size, String sortBy, Boolean ascending) {
        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                ascending ? Sort.Direction.ASC : Sort.Direction.DESC,
                sortBy);

        Page<TokenBalance> balances = tokenBalanceRepository.findAll(pageRequest);

        return balances.getContent().stream()
                .map(balance -> getUserTokenStats(balance.getUser().getId()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<UserTokenStats> searchUserStats(String searchTerm, Integer page, Integer size) {
        PageRequest pageRequest = PageRequest.of(page, size);

        Page<User> users = userRepository.searchUsers(searchTerm, pageRequest);

        return users.getContent().stream()
                .map(user -> getUserTokenStats(user.getId()))
                .collect(Collectors.toList());
    }

    public List<TokenTransaction> getTransactions(LocalDateTime startDate, LocalDateTime endDate) {
        log.info("Retrieving token transactions between {} and {}", startDate, endDate);
        try {
            // Get current user using UserContextService
            User currentUser = userContextService.getCurrentUser();

            List<TokenTransaction> transactions = tokenTransactionRepository.findByUserIdAndCreatedAtBetween(
                    currentUser.getId(),
                    startDate,
                    endDate);

            log.info("Found {} transactions in date range", transactions.size());
            return transactions;
        } catch (Exception e) {
            log.error("Error retrieving token transactions: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve token transactions", e);
        }
    }

    public List<TokenTransaction> getUserTransactions(Integer userId, int limit) {
        log.info("Retrieving token transactions for user: {}, limit: {}", userId, limit);
        try {
            List<TokenTransaction> transactions = tokenTransactionRepository.findByUser_IdOrderByCreatedAtDesc(userId);
            if (transactions.size() > limit) {
                transactions = transactions.subList(0, limit);
            }
            log.info("Found {} transactions for user {}", transactions.size(), userId);
            return transactions;
        } catch (Exception e) {
            log.error("Error retrieving token transactions for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve token transactions", e);
        }
    }

    public List<TokenRate> getTokenRates(String region) {
        log.info("Retrieving token rates for region: {}", region);
        try {
            if (region != null && !region.isEmpty()) {
                return tokenRateRepository.findByRegion(region);
            }
            return tokenRateRepository.findAll();
        } catch (Exception e) {
            log.error("Error retrieving token rates: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve token rates", e);
        }
    }

    private UserTokenStats getUserTokenStats(Integer userId) {
        User user = getUserById(userId);
        TokenBalance balance = getOrCreateBalance(user);
        DailyStats dailyStats = getDailyStats(userId);

        return UserTokenStats.builder()
                .userId(userId)
                .username(user.getUsername())
                .email(user.getEmail())
                .currentBalance(balance.getBalance())
                .totalAdsWatched(tokenTransactionRepository.countLifetimeAdsWatched(userId))
                .adsWatchedToday(dailyStats.getWatchedAds())
                .tokensEarnedToday(dailyStats.getTokensEarned())
                .tokensSpentToday(dailyStats.getTokensSpent())
                .lastActivity(balance.getLastActivityDate())
                .isActive(user.isActive())
                .build();
    }
}