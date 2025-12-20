package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.dto.*;
import com.orbvpn.api.domain.entity.TokenBalance;
import com.orbvpn.api.domain.entity.TokenRate;
import com.orbvpn.api.domain.entity.TokenTransaction;
import com.orbvpn.api.service.AdTokenServiceImpl;
import com.orbvpn.api.service.UserService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.time.LocalDateTime;
import java.util.List;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;
import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;

@Slf4j
@Controller
@RequiredArgsConstructor
public class TokenQueryResolver {
    private final AdTokenServiceImpl tokenService;
    private final UserService userService;

    @Secured(USER)
    @QueryMapping
    public TokenBalance getTokenBalance() {
        log.info("Fetching token balance for current user");
        try {
            TokenBalance balance = tokenService.getBalance(userService.getUser().getId());
            log.info("Successfully retrieved token balance");
            return balance;
        } catch (Exception e) {
            log.error("Error fetching token balance - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(USER)
    @QueryMapping
    public List<TokenTransaction> getTokenTransactions(
            @Argument LocalDateTime startDate,
            @Argument LocalDateTime endDate) {
        log.info("Fetching token transactions between {} and {}", startDate, endDate);
        try {
            return tokenService.getTransactions(startDate, endDate);
        } catch (Exception e) {
            log.error("Error fetching token transactions - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(USER)
    @QueryMapping
    public List<TokenRate> getTokenRates(@Argument String region) {
        log.info("Fetching token rates for region: {}", region);
        try {
            return tokenService.getTokenRates(region);
        } catch (Exception e) {
            log.error("Error fetching token rates for region: {} - Error: {}", region, e.getMessage(), e);
            throw e;
        }
    }

    @Secured(USER)
    @QueryMapping
    public DailyStats getDailyStats() {
        log.info("Fetching daily stats for current user");
        try {
            DailyStats stats = tokenService.getDailyStats(userService.getUser().getId());
            log.info("Successfully retrieved daily stats");
            return stats;
        } catch (Exception e) {
            log.error("Error fetching daily stats - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(USER)
    @QueryMapping
    public RemainingLimits getRemainingLimits() {
        log.info("Fetching remaining limits for current user");
        try {
            RemainingLimits limits = tokenService.getRemainingLimits(userService.getUser().getId());
            log.info("Successfully retrieved remaining limits");
            return limits;
        } catch (Exception e) {
            log.error("Error fetching remaining limits - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(USER)
    @QueryMapping
    public Boolean hasDailySubscription() {
        log.info("Checking daily subscription status for current user");
        try {
            return tokenService.hasDailySubscription(userService.getUser().getId());
        } catch (Exception e) {
            log.error("Error checking daily subscription status - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @QueryMapping
    public DailyStats getUserDailyStats(@Argument Integer userId) {
        log.info("Fetching daily stats for user: {}", userId);
        try {
            DailyStats stats = tokenService.getDailyStats(userId);
            log.info("Successfully retrieved daily stats for user: {}", userId);
            return stats;
        } catch (Exception e) {
            log.error("Error fetching daily stats for user: {} - Error: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @QueryMapping
    public RemainingLimits getUserRemainingLimits(@Argument Integer userId) {
        log.info("Fetching remaining limits for user: {}", userId);
        try {
            RemainingLimits limits = tokenService.getRemainingLimits(userId);
            log.info("Successfully retrieved remaining limits for user: {}", userId);
            return limits;
        } catch (Exception e) {
            log.error("Error fetching remaining limits for user: {} - Error: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @QueryMapping
    public Boolean hasUserDailySubscription(@Argument Integer userId) {
        log.info("Checking daily subscription status for user: {}", userId);
        try {
            return tokenService.hasDailySubscription(userId);
        } catch (Exception e) {
            log.error("Error checking daily subscription for user: {} - Error: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @QueryMapping
    public GlobalTokenStats getGlobalTokenStats() {
        log.info("Fetching global token statistics");
        try {
            GlobalTokenStats stats = tokenService.getGlobalStats();
            log.info("Successfully retrieved global token statistics");
            return stats;
        } catch (Exception e) {
            log.error("Error fetching global token stats - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @QueryMapping
    public List<UserTokenStats> getAllUserTokenStats(
            @Argument Integer page,
            @Argument Integer size,
            @Argument String sortBy,
            @Argument Boolean ascending) {
        log.info("Fetching user token stats - page: {}, size: {}, sortBy: {}, ascending: {}",
                page, size, sortBy, ascending);
        try {
            List<UserTokenStats> stats = tokenService.getAllUserStats(page, size, sortBy, ascending);
            log.info("Successfully retrieved token stats for {} users", stats.size());
            return stats;
        } catch (Exception e) {
            log.error("Error fetching user token stats - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @QueryMapping
    public List<UserTokenStats> searchUserTokenStats(
            @Argument String searchTerm,
            @Argument Integer page,
            @Argument Integer size) {
        log.info("Searching user token stats - term: {}, page: {}, size: {}", searchTerm, page, size);
        try {
            List<UserTokenStats> stats = tokenService.searchUserStats(searchTerm, page, size);
            log.info("Found {} matching user token stats", stats.size());
            return stats;
        } catch (Exception e) {
            log.error("Error searching user token stats - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @QueryMapping
    public TokenBalance getUserTokenBalance(@Argument Integer userId) {
        log.info("Fetching token balance for user: {}", userId);
        try {
            TokenBalance balance = tokenService.getBalance(userId);
            log.info("Successfully retrieved token balance for user: {}", userId);
            return balance;
        } catch (Exception e) {
            log.error("Error fetching token balance for user: {} - Error: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @QueryMapping
    public List<TokenTransaction> getUserTokenTransactions(
            @Argument Integer userId,
            @Argument Integer limit) {
        log.info("Fetching token transactions for user: {}, limit: {}", userId, limit);
        try {
            int actualLimit = limit != null ? limit : 50;
            List<TokenTransaction> transactions = tokenService.getUserTransactions(userId, actualLimit);
            log.info("Successfully retrieved {} transactions for user: {}", transactions.size(), userId);
            return transactions;
        } catch (Exception e) {
            log.error("Error fetching token transactions for user: {} - Error: {}", userId, e.getMessage(), e);
            throw e;
        }
    }
}