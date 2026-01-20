package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.*;
import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.exception.NotFoundException;
import com.orbvpn.api.service.AdTokenServiceImpl;
import com.orbvpn.api.service.AdVerificationService;
import com.orbvpn.api.service.TokenStakingService;
import com.orbvpn.api.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;

import jakarta.validation.Valid;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

@Slf4j
@Controller
@RequiredArgsConstructor
public class TokenMutationResolver {

    private final AdTokenServiceImpl tokenService;
    private final TokenStakingService stakingService;
    private final UserService userService;
    private final AdVerificationService adVerificationService;
    private final HttpServletRequest httpServletRequest;

    @MutationMapping
    public TokenBalance earnTokens(
            @Argument @NotBlank String adVendor,
            @Argument @NotBlank String region) {
        log.info("Earning tokens for vendor: {}, region: {}", adVendor, region);
        try {
            return tokenService.earnTokens(userService.getUser().getId(), adVendor, region);
        } catch (Exception e) {
            log.error("Error earning tokens - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @MutationMapping
    public TokenBalance spendTokens(
            @Argument @Min(1) int minutes,
            @Argument @Min(1) int activeDevices) {
        log.info("Spending tokens - minutes: {}, devices: {}", minutes, activeDevices);
        try {
            return tokenService.spendTokens(userService.getUser().getId(), minutes, activeDevices);
        } catch (Exception e) {
            log.error("Error spending tokens - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Request a new ad viewing session.
     * This is step 1 of the verified ad flow.
     */
    @MutationMapping
    public AdVerificationService.AdSessionResponse requestAdSession(
            @Argument @NotBlank String adVendor,
            @Argument String region,
            @Argument String deviceId) {
        log.info("Requesting ad session: vendor={}, region={}, device={}", adVendor, region, deviceId);
        try {
            Integer userId = userService.getUser().getId();
            String ipAddress = getClientIpAddress();
            return adVerificationService.requestAdSession(userId, adVendor, region, deviceId, ipAddress);
        } catch (Exception e) {
            log.error("Error requesting ad session - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Complete an ad viewing session and earn tokens.
     * This is step 2 of the verified ad flow.
     */
    @MutationMapping
    public TokenBalance completeAdSession(@Argument @Valid CompleteAdSessionInput input) {
        log.info("Completing ad session: sessionId={}", input.getSessionId());
        try {
            Integer userId = userService.getUser().getId();
            String ipAddress = getClientIpAddress();
            return adVerificationService.completeAdSession(
                userId,
                input.getSessionId(),
                input.getSignature(),
                input.getDurationSeconds(),
                ipAddress
            );
        } catch (Exception e) {
            log.error("Error completing ad session - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Get the client IP address from the request.
     */
    private String getClientIpAddress() {
        String xForwardedFor = httpServletRequest.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return httpServletRequest.getRemoteAddr();
    }

    @Secured(ADMIN)
    @MutationMapping
    public TokenRate updateTokenRate(@Argument @Valid TokenRateInput input) {
        log.info("Updating token rate");
        try {
            return tokenService.updateTokenRate(
                    input.getRegion(),
                    input.getAdVendor(),
                    input.getTokenPerAd(),
                    input.getTokenPerMinute(),
                    input.getDailyAdLimit(),
                    input.getHourlyAdLimit(),
                    input.getMinDailyAds(),
                    input.getMinWeeklyAds(),
                    input.getDeviceLimit(),
                    input.getMultiDeviceRate());
        } catch (Exception e) {
            log.error("Error updating token rate - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public boolean deleteTokenRate(@Argument @Min(1) Long id) {
        log.info("Deleting token rate: {}", id);
        try {
            return tokenService.deleteTokenRateById(id);
        } catch (Exception e) {
            log.error("Error deleting token rate - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public TokenStakingConfig createStakingConfig(@Argument @Valid TokenStakingConfigInput input) {
        log.info("Creating staking config");
        try {
            return stakingService.createConfig(input);
        } catch (Exception e) {
            log.error("Error creating staking config - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public TokenStakingConfig updateStakingConfig(
            @Argument @Min(1) Long id,
            @Argument @Valid TokenStakingConfigInput input) {
        log.info("Updating staking config: {}", id);
        try {
            return stakingService.updateConfig(id, input);
        } catch (Exception e) {
            log.error("Error updating staking config - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public Boolean deleteStakingConfig(@Argument @Min(1) Long id) {
        log.info("Deleting staking config: {}", id);
        try {
            return stakingService.deleteConfig(id);
        } catch (Exception e) {
            log.error("Error deleting staking config - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @MutationMapping
    public TokenStake stakeTokens(@Argument @Valid StakeTokensInput input) {
        log.info("Staking tokens: {}", input.getAmount());
        try {
            return stakingService.stakeTokens(input.getAmount(), input.getLockPeriodDays());
        } catch (Exception e) {
            log.error("Error staking tokens - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @MutationMapping
    public TokenStake unstakeTokens(@Argument @Min(1) Long stakeId) {
        log.info("Unstaking tokens for stake: {}", stakeId);
        try {
            return stakingService.unstakeTokens(stakeId);
        } catch (Exception e) {
            log.error("Error unstaking tokens - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public TokenStakingConfig toggleStakingConfig(@Argument @Min(1) Long id) {
        log.info("Toggling staking config with id: {}", id);
        try {
            TokenStakingConfig result = stakingService.toggleConfig(id);
            if (result != null) {
                log.info("Successfully toggled staking config. New active status: {}", result.getIsActive());
                return result;
            } else {
                log.error("Staking config toggle returned null for id: {}", id);
                throw new NotFoundException("Could not toggle staking config with id: " + id);
            }
        } catch (Exception e) {
            log.error("Error toggling staking config: {} - Error: {}", id, e.getMessage(), e);
            throw e;
        }
    }
}