package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.SubscriptionResponse;
import com.orbvpn.api.domain.dto.SubscriptionStatusDTO;
import com.orbvpn.api.domain.dto.UserSubscriptionView;
import com.orbvpn.api.domain.entity.Group;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.enums.GatewayName;
import com.orbvpn.api.filter.UserRateLimiter;
import com.orbvpn.api.service.GroupService;
import com.orbvpn.api.service.UserService;
import com.orbvpn.api.service.subscription.AppleService;
import com.orbvpn.api.service.subscription.GooglePlayService;
import com.orbvpn.api.service.subscription.RenewUserSubscriptionService;
import com.stripe.exception.StripeException;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;
import static com.orbvpn.api.domain.enums.RoleName.Constants.RESELLER;

@Slf4j
@Controller
@RequiredArgsConstructor
public class SubscriptionMutationResolver {
    private final RenewUserSubscriptionService renewSubscriptionService;
    private final UserService userService;
    private final GroupService groupService;
    private final AppleService appleService;
    private final GooglePlayService googlePlayService;
    private final UserRateLimiter userRateLimiter;

    @Secured(ADMIN)
    @MutationMapping
    public UserSubscriptionView renewWithDays(
            @Argument @Valid @NotBlank String username,
            @Argument @Valid @Min(1) int days) {
        log.info("Renewing subscription with {} days for user: {}", days, username);
        try {
            User user = userService.getUserByUsername(username);
            if (!userRateLimiter.isAllowedForUser(String.valueOf(user.getId()), user.getRole().getName().name())) {
                throw new RuntimeException("Rate limit exceeded for user: " + username);
            }
            return renewSubscriptionService.renewWithDayCount(user, days);
        } catch (Exception e) {
            log.error("Error renewing subscription - User: {} - Error: {}", username, e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public UserSubscriptionView renewSubscriptionWithCurrentGroup(
            @Argument @Valid @NotBlank String username) {
        log.info("Renewing subscription with current group for user: {}", username);
        try {
            User user = userService.getUserByUsername(username);
            if (!userRateLimiter.isAllowedForUser(String.valueOf(user.getId()), user.getRole().getName().name())) {
                throw new RuntimeException("Rate limit exceeded for user: " + username);
            }
            return renewSubscriptionService.renewUserSubscription(user);
        } catch (Exception e) {
            log.error("Error renewing subscription - User: {} - Error: {}", username, e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public UserSubscriptionView renewSubscriptionWithNewGroup(
            @Argument @Valid @NotBlank String username,
            @Argument @Valid @Min(1) int groupId) {
        log.info("Renewing subscription with new group for user: {}, groupId: {}", username, groupId);
        try {
            User user = userService.getUserByUsername(username);
            if (!userRateLimiter.isAllowedForUser(String.valueOf(user.getId()), user.getRole().getName().name())) {
                throw new RuntimeException("Rate limit exceeded for user: " + username);
            }
            Group group = groupService.getById(groupId);
            return renewSubscriptionService.renewUserSubscription(user, group);
        } catch (Exception e) {
            log.error("Error renewing subscription with new group - User: {} - Error: {}",
                    username, e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public UserSubscriptionView resetSubscriptionWithCurrentGroup(
            @Argument @Valid @NotBlank String username) {
        log.info("Resetting subscription with current group for user: {}", username);
        try {
            User user = userService.getUserByUsername(username);
            if (!userRateLimiter.isAllowedForUser(String.valueOf(user.getId()), user.getRole().getName().name())) {
                throw new RuntimeException("Rate limit exceeded for user: " + username);
            }
            return renewSubscriptionService.resetUserSubscription(user);
        } catch (Exception e) {
            log.error("Error resetting subscription - User: {} - Error: {}", username, e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public UserSubscriptionView resetSubscriptionWithNewGroup(
            @Argument @Valid @NotBlank String username,
            @Argument @Valid @Min(1) int groupId) {
        log.info("Resetting subscription with new group for user: {}, groupId: {}", username, groupId);
        try {
            User user = userService.getUserByUsername(username);
            if (!userRateLimiter.isAllowedForUser(String.valueOf(user.getId()), user.getRole().getName().name())) {
                throw new RuntimeException("Rate limit exceeded for user: " + username);
            }
            return renewSubscriptionService.resetUserSubscription(user, groupId);
        } catch (Exception e) {
            log.error("Error resetting subscription with new group - User: {} - Error: {}",
                    username, e.getMessage(), e);
            throw e;
        }
    }

    @Secured({ ADMIN, RESELLER })
    @MutationMapping
    public UserSubscriptionView resellerRenewSubscriptionWithCurrentGroup(
            @Argument @Valid @NotBlank String username) {
        log.info("Reseller renewing subscription with current group for user: {}", username);
        try {
            User user = userService.getUserByUsername(username);
            if (!userRateLimiter.isAllowedForUser(String.valueOf(user.getId()), user.getRole().getName().name())) {
                throw new RuntimeException("Rate limit exceeded for user: " + username);
            }
            return renewSubscriptionService.resellerRenewUserSubscription(user);
        } catch (Exception e) {
            log.error("Error in reseller subscription renewal - User: {} - Error: {}",
                    username, e.getMessage(), e);
            throw e;
        }
    }

    @Secured({ ADMIN, RESELLER })
    @MutationMapping
    public UserSubscriptionView resellerRenewSubscriptionWithNewGroup(
            @Argument @Valid @NotBlank String username,
            @Argument @Valid @Min(1) int groupId) {
        log.info("Reseller renewing subscription with new group for user: {}, groupId: {}", username, groupId);
        try {
            User user = userService.getUserByUsername(username);
            if (!userRateLimiter.isAllowedForUser(String.valueOf(user.getId()), user.getRole().getName().name())) {
                throw new RuntimeException("Rate limit exceeded for user: " + username);
            }
            Group group = groupService.getById(groupId);
            return renewSubscriptionService.resellerRenewUserSubscription(user, group);
        } catch (Exception e) {
            log.error("Error in reseller subscription renewal with new group - User: {} - Error: {}",
                    username, e.getMessage(), e);
            throw e;
        }
    }

    @Secured({ ADMIN, RESELLER })
    @MutationMapping
    public UserSubscriptionView resellerResetSubscriptionWithCurrentGroup(
            @Argument @Valid @NotBlank String username) {
        log.info("Reseller resetting subscription with current group for user: {}", username);
        try {
            User user = userService.getUserByUsername(username);
            if (!userRateLimiter.isAllowedForUser(String.valueOf(user.getId()), user.getRole().getName().name())) {
                throw new RuntimeException("Rate limit exceeded for user: " + username);
            }
            return renewSubscriptionService.resellerResetUserSubscription(user);
        } catch (Exception e) {
            log.error("Error in reseller subscription reset - User: {} - Error: {}",
                    username, e.getMessage(), e);
            throw e;
        }
    }

    @Secured({ ADMIN, RESELLER })
    @MutationMapping
    public UserSubscriptionView resellerResetSubscriptionWithNewGroup(
            @Argument @Valid @NotBlank String username,
            @Argument @Valid @Min(1) int groupId) {
        log.info("Reseller resetting subscription with new group for user: {}, groupId: {}", username, groupId);
        try {
            User user = userService.getUserByUsername(username);
            if (!userRateLimiter.isAllowedForUser(String.valueOf(user.getId()), user.getRole().getName().name())) {
                throw new RuntimeException("Rate limit exceeded for user: " + username);
            }
            Group group = groupService.getById(groupId);
            return renewSubscriptionService.resellerResetUserSubscription(user, group);
        } catch (Exception e) {
            log.error("Error in reseller subscription reset with new group - User: {} - Error: {}",
                    username, e.getMessage(), e);
            throw e;
        }
    }

    @MutationMapping
    public SubscriptionResponse verifyAndAssignSubscription(
            @Argument @Valid @Min(1) int userId,
            @Argument @Valid @NotBlank String purchaseToken,
            @Argument @Valid @NotBlank String platform,
            @Argument String subscriptionId,
            @Argument String packageName) throws StripeException {
        log.info("Verifying and assigning subscription - UserId: {}, Platform: {}", userId, platform);

        try {
            User user = userService.getUserById(userId);
            String productId = null;
            LocalDateTime expiresAt = null;
            Boolean isTrialPeriod = false;

            // Process based on platform
            String upperPlatform = platform.toUpperCase();

            if (upperPlatform.equals("APP_STORE") || upperPlatform.equals("APPLE")
                    || upperPlatform.equals("APPLE_STORE")) {
                var subscriptionData = appleService.getSubscriptionData(
                        purchaseToken, "unknown", user);

                expiresAt = subscriptionData.getExpiresAt();
                productId = subscriptionData.getOriginalTransactionId();
                isTrialPeriod = subscriptionData.getIsTrialPeriod();

                renewSubscriptionService.assignSubscription(
                        user,
                        subscriptionData.getGroupId(),
                        expiresAt,
                        GatewayName.APPLE_STORE,
                        true,
                        subscriptionData.getOriginalTransactionId(),
                        isTrialPeriod,
                        subscriptionData.getOriginalTransactionId());
            } else if (upperPlatform.equals("GOOGLE_PLAY") || upperPlatform.equals("GOOGLE")
                    || upperPlatform.equals("GOOGLE_PLAY_STORE")) {
                if (packageName == null || packageName.trim().isEmpty()) {
                    packageName = "com.orbvpn.android";
                }

                var subscriptionInfo = googlePlayService.verifyTokenWithGooglePlay(
                        packageName,
                        purchaseToken,
                        subscriptionId,
                        "unknown",
                        user,
                        null);

                expiresAt = subscriptionInfo.getExpiresAt();
                productId = subscriptionInfo.getOrderId();
                // Check if this is a trial period (if that information is available)
                isTrialPeriod = subscriptionInfo.getIsTrialPeriod() != null ? subscriptionInfo.getIsTrialPeriod()
                        : false;

                renewSubscriptionService.assignSubscription(
                        user,
                        subscriptionInfo.getGroupId(),
                        subscriptionInfo.getExpiresAt(),
                        GatewayName.GOOGLE_PLAY,
                        true,
                        purchaseToken,
                        isTrialPeriod,
                        subscriptionId);

                // Try to acknowledge the purchase
                googlePlayService.acknowledgePurchase(subscriptionId, purchaseToken);
            } else {
                throw new IllegalArgumentException("Unsupported platform: " + platform);
            }

            SubscriptionStatusDTO status = new SubscriptionStatusDTO(
                    productId, LocalDateTime.now(), expiresAt, true);

            return new SubscriptionResponse(true, "Subscription successfully assigned.", status);

        } catch (Exception e) {
            log.error("Error verifying/assigning subscription: {}", e.getMessage(), e);
            return new SubscriptionResponse(false, "Error: " + e.getMessage(), null);
        }
    }
}