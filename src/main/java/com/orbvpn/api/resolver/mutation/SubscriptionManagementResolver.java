package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.UserSubscriptionView;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.enums.RoleName;
import com.orbvpn.api.exception.UnauthorizedException;
import com.orbvpn.api.service.UserService;
import com.orbvpn.api.service.subscription.SubscriptionManagementService;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;
import static com.orbvpn.api.domain.enums.RoleName.Constants.RESELLER;

@Slf4j
@Controller
@RequiredArgsConstructor
public class SubscriptionManagementResolver {

    private final SubscriptionManagementService subscriptionManagementService;
    private final UserService userService;

    private void validateAdminOrReseller() {
        User currentUser = userService.getUser();
        RoleName roleName = currentUser.getRole().getName();
        if (roleName != RoleName.ADMIN && roleName != RoleName.RESELLER) {
            throw new UnauthorizedException("Only admins and resellers can manage subscriptions");
        }
    }

    private void validateUserAccess(User targetUser) {
        User currentUser = userService.getUser();
        RoleName roleName = currentUser.getRole().getName();

        // Admins can access all users
        if (roleName == RoleName.ADMIN) {
            return;
        }

        // Resellers can only access their own users
        if (roleName == RoleName.RESELLER) {
            if (!currentUser.equals(targetUser.getReseller().getUser())) {
                throw new UnauthorizedException("Resellers can only manage their own users' subscriptions");
            }
        }
    }

    @Secured({ ADMIN, RESELLER })
    @MutationMapping
    public UserSubscriptionView removeUserSubscription(
            @Argument @Valid @NotBlank String username) {
        log.info("Removing subscription for user: {}", username);
        try {
            validateAdminOrReseller();
            User user = userService.getUserByUsername(username);
            validateUserAccess(user);
            return subscriptionManagementService.removeUserSubscription(user);
        } catch (Exception e) {
            log.error("Error removing subscription - User: {} - Error: {}", username, e.getMessage(), e);
            throw e;
        }
    }

    @Secured({ ADMIN, RESELLER })
    @MutationMapping
    public UserSubscriptionView revertLastSubscriptionChange(
            @Argument @Valid @NotBlank String username) {
        log.info("Reverting last subscription change for user: {}", username);
        try {
            validateAdminOrReseller();
            User user = userService.getUserByUsername(username);
            validateUserAccess(user);
            return subscriptionManagementService.revertLastSubscriptionChange(user);
        } catch (Exception e) {
            log.error("Error reverting subscription - User: {} - Error: {}", username, e.getMessage(), e);
            throw e;
        }
    }

    @Secured({ ADMIN, RESELLER })
    @MutationMapping
    public UserSubscriptionView revertSubscriptionToDays(
            @Argument @Valid @NotBlank String username,
            @Argument @Valid @Min(0) int remainingDays) {
        log.info("Reverting subscription to {} days for user: {}", remainingDays, username);
        try {
            validateAdminOrReseller();
            if (remainingDays < 0) {
                throw new IllegalArgumentException("Remaining days must be greater than or equal to 0");
            }
            User user = userService.getUserByUsername(username);
            validateUserAccess(user);
            return subscriptionManagementService.revertSubscriptionToDays(user, remainingDays);
        } catch (Exception e) {
            log.error("Error reverting subscription to days - User: {} - Error: {}",
                    username, e.getMessage(), e);
            throw e;
        }
    }
}