package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.domain.dto.StakingStats;
import com.orbvpn.api.service.TokenStakingService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;
import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;

@Slf4j
@Controller
@RequiredArgsConstructor
public class TokenStakingQueryResolver {
    private final TokenStakingService stakingService;

    @Secured(ADMIN)
    @QueryMapping
    public List<TokenStakingConfig> getAllStakingConfigs() {
        log.info("Fetching all staking configurations");
        try {
            List<TokenStakingConfig> configs = stakingService.getAllConfigs();
            log.info("Successfully retrieved {} staking configurations", configs.size());
            return configs;
        } catch (Exception e) {
            log.error("Error fetching staking configurations - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @QueryMapping
    public TokenStakingConfig getStakingConfig(@Argument Long id) {
        log.info("Fetching staking configuration with id: {}", id);
        try {
            TokenStakingConfig config = stakingService.getConfig(id);
            log.info("Successfully retrieved staking configuration: {}", id);
            return config;
        } catch (Exception e) {
            log.error("Error fetching staking configuration: {} - Error: {}", id, e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @QueryMapping
    public StakingStats getGlobalStakingStats() {
        log.info("Fetching global staking statistics");
        try {
            StakingStats stats = stakingService.getGlobalStats();
            log.info("Successfully retrieved global staking statistics");
            return stats;
        } catch (Exception e) {
            log.error("Error fetching global staking stats - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @QueryMapping
    public StakingStats getUserStakingStats(@Argument Integer userId) {
        log.info("Fetching staking statistics for user: {}", userId);
        try {
            StakingStats stats = stakingService.getUserStats(userId);
            log.info("Successfully retrieved staking statistics for user: {}", userId);
            return stats;
        } catch (Exception e) {
            log.error("Error fetching staking stats for user: {} - Error: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    @Secured(USER)
    @QueryMapping
    public List<TokenStakingConfig> getMyStakingOptions() {
        log.info("Fetching available staking options for current user");
        try {
            List<TokenStakingConfig> options = stakingService.getAvailableStakingOptions();
            log.info("Successfully retrieved {} staking options", options.size());
            return options;
        } catch (Exception e) {
            log.error("Error fetching staking options - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(USER)
    @QueryMapping
    public List<TokenStake> getMyActiveStakes() {
        log.info("Fetching active stakes for current user");
        try {
            List<TokenStake> stakes = stakingService.getCurrentUserActiveStakes();
            log.info("Successfully retrieved {} active stakes", stakes.size());
            return stakes;
        } catch (Exception e) {
            log.error("Error fetching active stakes - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(USER)
    @QueryMapping
    public StakingStats getMyStakingStats() {
        log.info("Fetching staking statistics for current user");
        try {
            StakingStats stats = stakingService.getCurrentUserStats();
            log.info("Successfully retrieved current user staking statistics");
            return stats;
        } catch (Exception e) {
            log.error("Error fetching current user staking stats - Error: {}", e.getMessage(), e);
            throw e;
        }
    }
}