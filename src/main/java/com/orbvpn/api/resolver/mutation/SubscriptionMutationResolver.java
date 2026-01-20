package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.SubscriptionResponse;
import com.orbvpn.api.domain.dto.SubscriptionStatusDTO;
import com.orbvpn.api.domain.dto.UserSubscriptionView;
import com.orbvpn.api.domain.entity.Group;
import com.orbvpn.api.domain.entity.Reseller;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.domain.enums.GatewayName;
import com.orbvpn.api.domain.enums.RoleName;
import com.orbvpn.api.exception.InsufficientFundsException;
import com.orbvpn.api.filter.UserRateLimiter;
import com.orbvpn.api.service.GroupService;
import com.orbvpn.api.service.UserService;
import com.orbvpn.api.service.audit.AdminAuditService;
import com.orbvpn.api.service.reseller.ResellerService;
import com.orbvpn.api.service.reseller.ResellerUserService;
import com.orbvpn.api.service.subscription.AppleService;
import com.orbvpn.api.service.subscription.GooglePlayService;
import com.orbvpn.api.service.subscription.RenewUserSubscriptionService;
import com.orbvpn.api.service.subscription.UserSubscriptionService;
import com.orbvpn.api.domain.entity.AdminAuditLog;

import java.util.HashMap;
import java.util.Map;
import com.stripe.exception.StripeException;

import java.math.BigDecimal;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
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
    private final ResellerService resellerService;
    private final ResellerUserService resellerUserService;
    private final UserSubscriptionService userSubscriptionService;
    private final AdminAuditService adminAuditService;

    @Secured(ADMIN)
    @MutationMapping
    @Transactional
    public UserSubscriptionView renewWithDays(
            @Argument @Valid @NotBlank String username,
            @Argument @Valid @Min(1) int days) {
        log.info("Renewing subscription with {} days for user: {}", days, username);
        try {
            User user = userService.getUserByUsername(username);
            if (!userRateLimiter.isAllowedForUser(String.valueOf(user.getId()), user.getRole().getName().name())) {
                throw new RuntimeException("Rate limit exceeded for user: " + username);
            }

            // Capture before state for audit
            UserSubscription beforeSub = userSubscriptionService.getCurrentSubscription(user);
            Map<String, Object> beforeState = captureSubscriptionState(beforeSub);

            UserSubscriptionView result = renewSubscriptionService.renewWithDayCount(user, days);

            // Audit log
            Map<String, Object> afterState = new HashMap<>();
            afterState.put("expiresAt", result.getExpiresAt());
            afterState.put("daysAdded", days);

            adminAuditService.logSubscriptionAction(
                    AdminAuditLog.ACTION_ADD_DAYS,
                    user, beforeState, afterState,
                    String.format("Added %d days to subscription", days));

            return result;
        } catch (Exception e) {
            log.error("Error renewing subscription - User: {} - Error: {}", username, e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    @Transactional
    public UserSubscriptionView renewSubscriptionWithCurrentGroup(
            @Argument @Valid @NotBlank String username) {
        log.info("Renewing subscription with current group for user: {}", username);
        try {
            User user = userService.getUserByUsername(username);
            if (!userRateLimiter.isAllowedForUser(String.valueOf(user.getId()), user.getRole().getName().name())) {
                throw new RuntimeException("Rate limit exceeded for user: " + username);
            }

            // Capture before state for audit
            UserSubscription beforeSub = userSubscriptionService.getCurrentSubscription(user);
            Map<String, Object> beforeState = captureSubscriptionState(beforeSub);

            UserSubscriptionView result = renewSubscriptionService.renewUserSubscription(user);

            // Audit log
            Map<String, Object> afterState = new HashMap<>();
            afterState.put("expiresAt", result.getExpiresAt());
            if (beforeSub != null && beforeSub.getGroup() != null) {
                afterState.put("groupName", beforeSub.getGroup().getName());
            }

            adminAuditService.logSubscriptionAction(
                    AdminAuditLog.ACTION_RENEW_SUBSCRIPTION,
                    user, beforeState, afterState,
                    "Renewed subscription with current group");

            return result;
        } catch (Exception e) {
            log.error("Error renewing subscription - User: {} - Error: {}", username, e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    @Transactional
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

            // Capture before state for audit
            UserSubscription beforeSub = userSubscriptionService.getCurrentSubscription(user);
            Map<String, Object> beforeState = captureSubscriptionState(beforeSub);

            UserSubscriptionView result = renewSubscriptionService.renewUserSubscription(user, group);

            // Audit log
            Map<String, Object> afterState = new HashMap<>();
            afterState.put("expiresAt", result.getExpiresAt());
            afterState.put("newGroupId", groupId);
            afterState.put("newGroupName", group.getName());

            adminAuditService.logSubscriptionAction(
                    AdminAuditLog.ACTION_CHANGE_PLAN,
                    user, beforeState, afterState,
                    String.format("Changed plan to %s and renewed", group.getName()));

            return result;
        } catch (Exception e) {
            log.error("Error renewing subscription with new group - User: {} - Error: {}",
                    username, e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    @Transactional
    public UserSubscriptionView resetSubscriptionWithCurrentGroup(
            @Argument @Valid @NotBlank String username) {
        log.info("Resetting subscription with current group for user: {}", username);
        try {
            User user = userService.getUserByUsername(username);
            if (!userRateLimiter.isAllowedForUser(String.valueOf(user.getId()), user.getRole().getName().name())) {
                throw new RuntimeException("Rate limit exceeded for user: " + username);
            }

            // Capture before state for audit
            UserSubscription beforeSub = userSubscriptionService.getCurrentSubscription(user);
            Map<String, Object> beforeState = captureSubscriptionState(beforeSub);

            UserSubscriptionView result = renewSubscriptionService.resetUserSubscription(user);

            // Audit log
            Map<String, Object> afterState = new HashMap<>();
            afterState.put("expiresAt", result.getExpiresAt());
            if (beforeSub != null && beforeSub.getGroup() != null) {
                afterState.put("groupName", beforeSub.getGroup().getName());
            }

            adminAuditService.logSubscriptionAction(
                    AdminAuditLog.ACTION_RESET_SUBSCRIPTION,
                    user, beforeState, afterState,
                    "Reset subscription with current group");

            return result;
        } catch (Exception e) {
            log.error("Error resetting subscription - User: {} - Error: {}", username, e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    @Transactional
    public UserSubscriptionView resetSubscriptionWithNewGroup(
            @Argument @Valid @NotBlank String username,
            @Argument @Valid @Min(1) int groupId) {
        log.info("Resetting subscription with new group for user: {}, groupId: {}", username, groupId);
        try {
            User user = userService.getUserByUsername(username);
            if (!userRateLimiter.isAllowedForUser(String.valueOf(user.getId()), user.getRole().getName().name())) {
                throw new RuntimeException("Rate limit exceeded for user: " + username);
            }
            Group group = groupService.getById(groupId);

            // Capture before state for audit
            UserSubscription beforeSub = userSubscriptionService.getCurrentSubscription(user);
            Map<String, Object> beforeState = captureSubscriptionState(beforeSub);

            UserSubscriptionView result = renewSubscriptionService.resetUserSubscription(user, groupId);

            // Audit log
            Map<String, Object> afterState = new HashMap<>();
            afterState.put("expiresAt", result.getExpiresAt());
            afterState.put("newGroupId", groupId);
            afterState.put("newGroupName", group.getName());

            adminAuditService.logSubscriptionAction(
                    AdminAuditLog.ACTION_RESET_SUBSCRIPTION,
                    user, beforeState, afterState,
                    String.format("Reset subscription with new plan: %s", group.getName()));

            return result;
        } catch (Exception e) {
            log.error("Error resetting subscription with new group - User: {} - Error: {}",
                    username, e.getMessage(), e);
            throw e;
        }
    }

    @Secured({ ADMIN, RESELLER })
    @MutationMapping
    @Transactional
    public UserSubscriptionView resellerRenewSubscriptionWithCurrentGroup(
            @Argument @Valid @NotBlank String username) {
        log.info("Reseller renewing subscription with current group for user: {}", username);
        try {
            User user = userService.getUserByUsername(username);
            if (!userRateLimiter.isAllowedForUser(String.valueOf(user.getId()), user.getRole().getName().name())) {
                throw new RuntimeException("Rate limit exceeded for user: " + username);
            }

            // Get current subscription to determine the group
            UserSubscription currentSubscription = userSubscriptionService.getCurrentSubscription(user);
            if (currentSubscription == null || currentSubscription.getGroup() == null) {
                throw new RuntimeException("User has no active subscription to renew");
            }
            Group group = currentSubscription.getGroup();

            // Deduct credit before renewing
            deductResellerCreditForSubscription(group, user, "Subscription renewal");

            return renewSubscriptionService.resellerRenewUserSubscription(user);
        } catch (Exception e) {
            log.error("Error in reseller subscription renewal - User: {} - Error: {}",
                    username, e.getMessage(), e);
            throw e;
        }
    }

    @Secured({ ADMIN, RESELLER })
    @MutationMapping
    @Transactional
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

            // Deduct credit before renewing with new group
            deductResellerCreditForSubscription(group, user, "Subscription renewal with plan change");

            return renewSubscriptionService.resellerRenewUserSubscription(user, group);
        } catch (Exception e) {
            log.error("Error in reseller subscription renewal with new group - User: {} - Error: {}",
                    username, e.getMessage(), e);
            throw e;
        }
    }

    @Secured({ ADMIN, RESELLER })
    @MutationMapping
    @Transactional
    public UserSubscriptionView resellerResetSubscriptionWithCurrentGroup(
            @Argument @Valid @NotBlank String username) {
        log.info("Reseller resetting subscription with current group for user: {}", username);
        try {
            User user = userService.getUserByUsername(username);
            if (!userRateLimiter.isAllowedForUser(String.valueOf(user.getId()), user.getRole().getName().name())) {
                throw new RuntimeException("Rate limit exceeded for user: " + username);
            }

            // Get current subscription to determine the group
            UserSubscription currentSubscription = userSubscriptionService.getCurrentSubscription(user);
            if (currentSubscription == null || currentSubscription.getGroup() == null) {
                throw new RuntimeException("User has no active subscription to reset");
            }
            Group group = currentSubscription.getGroup();

            // Deduct credit before resetting
            deductResellerCreditForSubscription(group, user, "Subscription reset");

            return renewSubscriptionService.resellerResetUserSubscription(user);
        } catch (Exception e) {
            log.error("Error in reseller subscription reset - User: {} - Error: {}",
                    username, e.getMessage(), e);
            throw e;
        }
    }

    @Secured({ ADMIN, RESELLER })
    @MutationMapping
    @Transactional
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

            // Deduct credit before resetting with new group
            deductResellerCreditForSubscription(group, user, "Subscription reset with plan change");

            return renewSubscriptionService.resellerResetUserSubscription(user, group);
        } catch (Exception e) {
            log.error("Error in reseller subscription reset with new group - User: {} - Error: {}",
                    username, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * SECURITY: Rate limited to prevent abuse of subscription assignment.
     * Rate limit: 5 requests per user per minute.
     */
    @MutationMapping
    @Transactional
    public SubscriptionResponse verifyAndAssignSubscription(
            @Argument @Valid @Min(1) int userId,
            @Argument @Valid @NotBlank String purchaseToken,
            @Argument @Valid @NotBlank String platform,
            @Argument String subscriptionId,
            @Argument String packageName) throws StripeException {
        log.info("Verifying and assigning subscription - UserId: {}, Platform: {}", userId, platform);

        try {
            User user = userService.getUserById(userId);

            // SECURITY: Apply rate limiting to prevent subscription abuse
            if (!userRateLimiter.isAllowedForUser(String.valueOf(userId), "USER")) {
                log.warn("SECURITY: Rate limit exceeded for subscription assignment - UserId: {}", userId);
                return new SubscriptionResponse(false,
                        "Too many subscription requests. Please wait a moment and try again.", null);
            }
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

    /**
     * Deducts credit from the reseller for subscription operations.
     * Uses the same pricing logic as ResellerUserService.calculatePrice() for consistency.
     * Skips deduction for admins and when calculated price is zero (OWNER level).
     *
     * @param group The group being subscribed to
     * @param targetUser The user receiving the subscription
     * @param reason Description for the credit transaction
     */
    private void deductResellerCreditForSubscription(Group group, User targetUser, String reason) {
        User currentUser = userService.getUser();

        // Skip if current user is admin (admins don't have credits)
        if (currentUser.getRole().getName() == RoleName.ADMIN) {
            log.debug("Skipping credit deduction - current user is admin");
            return;
        }

        Reseller reseller = currentUser.getReseller();
        if (reseller == null) {
            log.warn("Current user {} has no reseller assigned", currentUser.getEmail());
            return;
        }

        // Use existing price calculation method for consistency
        // This handles OWNER level (returns zero) and applies level discounts
        BigDecimal price = resellerUserService.calculatePrice(reseller, group);

        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("No credit deduction needed - price is zero (likely OWNER level)");
            return;
        }

        // Check sufficient credit
        if (reseller.getCredit().compareTo(price) < 0) {
            log.error("Insufficient credit for reseller {}. Required: {}, Available: {}",
                    reseller.getId(), price, reseller.getCredit());
            throw new InsufficientFundsException(
                    String.format("Insufficient credit. Required: $%.2f, Available: $%.2f",
                            price, reseller.getCredit()));
        }

        // Deduct the credit
        String fullReason = String.format("%s - User: %s, Group: %s",
                reason, targetUser.getEmail(), group.getName());
        resellerService.deductResellerCredit(reseller.getId(), price, fullReason, currentUser.getEmail());

        log.info("Deducted {} credit from reseller {} for {}", price, reseller.getId(), fullReason);
    }

    /**
     * Helper method to capture subscription state for audit logging
     */
    private Map<String, Object> captureSubscriptionState(UserSubscription subscription) {
        Map<String, Object> state = new HashMap<>();
        if (subscription != null) {
            state.put("expiresAt", subscription.getExpiresAt());
            state.put("duration", subscription.getDuration());
            state.put("multiLoginCount", subscription.getMultiLoginCount());
            if (subscription.getGroup() != null) {
                state.put("groupId", subscription.getGroup().getId());
                state.put("groupName", subscription.getGroup().getName());
            }
        }
        return state;
    }
}