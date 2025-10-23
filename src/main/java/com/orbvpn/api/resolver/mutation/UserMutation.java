package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.config.security.Unsecured;
import com.orbvpn.api.domain.ValidationProperties;
import com.orbvpn.api.domain.dto.AuthenticatedUser;
import com.orbvpn.api.domain.dto.UserCreate;
import com.orbvpn.api.domain.entity.Group;
import com.orbvpn.api.domain.entity.RadCheck;
import com.orbvpn.api.domain.dto.UserProfileEdit;
import com.orbvpn.api.domain.dto.UserProfileView;
import com.orbvpn.api.domain.dto.UserView;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserDevice;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.mapper.UserViewMapper;
import com.orbvpn.api.service.GroupService;
import com.orbvpn.api.service.UserDeviceService;
import com.orbvpn.api.service.UserService;
import com.orbvpn.api.service.subscription.UserSubscriptionService;
import com.orbvpn.api.service.notification.FCMService;
import com.orbvpn.api.service.notification.NotificationService;
import com.orbvpn.api.utils.Utilities;
import com.orbvpn.api.service.RadiusService;
import com.orbvpn.api.repository.RadCheckRepository;
import com.orbvpn.api.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import static com.orbvpn.api.domain.ValidationProperties.BAD_PASSWORD_MESSAGE;
import static com.orbvpn.api.domain.ValidationProperties.PASSWORD_PATTERN;
import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;
import lombok.extern.slf4j.Slf4j;
import java.math.BigInteger;
import java.util.List;
import java.util.Locale;
import org.springframework.util.StringUtils;

@Controller
@Slf4j
@RequiredArgsConstructor
@Validated
public class UserMutation {

    private final UserService userService;
    private final UserSubscriptionService userSubscriptionService;
    private final GroupService groupService;
    private final UserViewMapper userViewMapper;
    private final NotificationService notificationService;
    private final UserDeviceService userDeviceService;
    private final FCMService fcmService;
    private final RadiusService radiusService;
    private final RadCheckRepository radCheckRepository;

    @Unsecured
    @MutationMapping
    public AuthenticatedUser register(@Argument @Valid UserCreate userCreate) {
        return userService.register(userCreate);
    }

    @Unsecured
    @MutationMapping
    public AuthenticatedUser login(
            @Argument String email,
            @Argument String password) {
        log.info("Login mutation called with email: {}", email);
        try {
            AuthenticatedUser result = userService.login(email, password);
            if (result == null) {
                throw new BadCredentialsException("Authentication failed");
            }
            log.info("Login successful for user: {}", email);
            return result;
        } catch (Exception e) {
            log.error("Login failed for user: {} - Error: {}", email, e.getMessage(), e);
            throw e;
        }
    }

    @Unsecured
    @MutationMapping
    public boolean requestResetPassword(@Argument String email) {
        log.info("Password reset requested for email: {}", email);
        try {
            boolean result = userService.requestResetPassword(email);
            log.info("Password reset request processed for email: {}", email);
            return result;
        } catch (Exception e) {
            log.error("Password reset request failed for email: {} - Error: {}", email, e.getMessage(), e);
            throw e;
        }
    }

    @Unsecured
    @MutationMapping
    public boolean resetPassword(
            @Argument @NotBlank String token,
            @Argument @Pattern(regexp = ValidationProperties.PASSWORD_PATTERN, message = ValidationProperties.BAD_PASSWORD_MESSAGE) String password) {
        log.info("Password reset initiated with token: {}", token);
        try {
            boolean result = userService.resetPassword(token, password);
            log.info("Password reset completed successfully");
            return result;
        } catch (Exception e) {
            log.error("Password reset failed - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @MutationMapping
    public boolean changePassword(
            @Argument String oldPassword,
            @Argument @Pattern(regexp = PASSWORD_PATTERN, message = BAD_PASSWORD_MESSAGE) String password) {
        User userDetails = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        int id = userDetails.getId();
        boolean result = userService.changePassword(id, oldPassword, password);
        if (result) {
            try {
                List<UserDevice> activeDevices = userDeviceService.getActiveDevicesByUserId(id);
                for (UserDevice device : activeDevices) {
                    userDeviceService.logoutDevice(device);
                    fcmService.sendLogoutNotification(device.getFcmToken());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    @Transactional
    @MutationMapping
    public UserProfileView editProfile(@Argument UserProfileEdit userProfile) {
        return userService.editProfile(userProfile);
    }

    @MutationMapping
    public Boolean editProfileByAdmin(
            @Argument int id,
            @Argument UserCreate updatedUser,
            @Argument UserProfileEdit updatedProfile) {
        User user = userService.getUserById(id);
        userService.updateUser(user, updatedUser);
        userService.editProfileByAdmin(user, updatedProfile);
        return true;
    }

    @MutationMapping
    public Boolean editAutoRenew(@Argument Boolean isActive) {
        return userService.editAutoRenew(isActive);
    }

    @RolesAllowed(ADMIN)
    @MutationMapping
    public boolean createUser(@Argument UserCreate user, @Argument UserProfileEdit userProfile) {
        User createdUser = userService.createUser(user);
        userService.editProfileByAdmin(createdUser, userProfile);
        return true;
    }

    @RolesAllowed(ADMIN)
    @MutationMapping
    public boolean deleteBundleUsers(@Argument int[] ids) {
        for (int id : ids) {
            userService.deleteUser(userService.getUserById(id));
        }
        return true;
    }

    @RolesAllowed(ADMIN)
    @MutationMapping
    public UserView createNewUserByAdmin(
            @Argument int groupId,
            @Argument int resellerId,
            @Argument String firstName,
            @Argument String lastName,
            @Argument String userName,
            @Argument String email,
            @Argument String devices,
            @Argument String country,
            @Argument String phone,
            @Argument String language) {

        String randomPassword = Utilities.getRandomPassword(10);
        User user = userService.createUserByAdmin(resellerId, email, userName, randomPassword);

        UserProfileEdit userProfile = UserProfileEdit.builder()
                .firstName(firstName)
                .lastName(lastName)
                .country(country)
                .phone(phone)
                .language(language != null ? new Locale(language) : null)
                .build();
        userService.editProfileByAdmin(user, userProfile);

        Group group = groupService.getById(groupId);
        UserSubscription subscription = userSubscriptionService.createSubscriptionByAdmin(user, group);

        notificationService.welcomingNewUsersCreatedByAdmin(user, subscription, randomPassword);

        return userViewMapper.toView(user);
    }

    @RolesAllowed(ADMIN)
    @MutationMapping
    public boolean deleteUser(@Argument int id) {
        userService.deleteUser(userService.getUserById(id));
        return true;
    }

    @RolesAllowed({ "USER" })
    @MutationMapping
    public boolean deleteUserAccount() {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        return userService.deleteUserAccount(currentUsername);
    }

    @RolesAllowed(ADMIN)
    @MutationMapping
    public boolean updateSubscription(
            @Argument int userId,
            @Argument int multiLoginCount,
            @Argument BigInteger dailyBandwidth,
            @Argument BigInteger downloadUpload) {

        UserSubscription userSubscription = userSubscriptionService
                .getCurrentSubscription(userService.getUserById(userId));
        userSubscription.setMultiLoginCount(multiLoginCount);
        userSubscription.setDailyBandwidth(dailyBandwidth);
        userSubscription.setDownloadUpload(downloadUpload);
        userSubscriptionService.save(userSubscription);

        return true;
    }

    @RolesAllowed(ADMIN)
    @MutationMapping
    public String reEncryptUserPasswords(
            @Argument int fromUserId,
            @Argument int toUserId,
            @Argument boolean sendEmail) {
        userService.reEncryptPasswordsInRange(fromUserId, toUserId, sendEmail);
        return "Password re-encryption completed for users between ID " + fromUserId + " and " + toUserId + ".";
    }

    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @MutationMapping
    public Boolean deleteDevices(
            @Argument Integer userId,
            @Argument String username) {
        User user;

        if (userId != null) {
            user = userService.getUserById(userId);
        } else if (username != null && !username.isEmpty()) {
            user = userService.getUserByUsername(username);
        } else {
            user = userService.getUser();
        }

        if (user == null) {
            throw new NotFoundException("User not found");
        }

        userDeviceService.deleteUserDevices(user);
        return true;
    }

    @RolesAllowed(ADMIN)
    @MutationMapping
    public String synchronizeRadCheckPasswords(
            @Argument @Valid @Min(value = 1, message = "From user ID must be positive") int fromUserId,
            @Argument @Valid @Min(value = 1, message = "To user ID must be positive") int toUserId) {
        log.info("Synchronizing RadCheck passwords for users from ID {} to {}", fromUserId, toUserId);

        try {
            List<User> usersInRange = userService.getUsersByIdRange(fromUserId, toUserId);
            int processedCount = 0;
            int syncedCount = 0;

            for (User user : usersInRange) {
                try {
                    processedCount++;

                    if (StringUtils.isEmpty(user.getRadAccess())) {
                        log.warn("User {} has no radAccess value, skipping", user.getUsername());
                        continue;
                    }

                    if (!radiusService.hasValidRadCheckPassword(user)) {
                        radiusService.synchronizeUserPassword(user);
                        syncedCount++;
                        log.info("Synchronized RadCheck password for user: {}", user.getUsername());
                    } else {
                        log.debug("User {} already has valid RadCheck password", user.getUsername());
                    }

                } catch (Exception e) {
                    log.error("Failed to synchronize RadCheck for user {}: {}", user.getUsername(), e.getMessage(), e);
                }
            }

            String result = String.format(
                    "RadCheck synchronization completed. Processed: %d users, Synchronized: %d users",
                    processedCount, syncedCount);
            log.info(result);
            return result;

        } catch (Exception e) {
            log.error("Error during RadCheck synchronization: {}", e.getMessage(), e);
            throw e;
        }
    }

    @RolesAllowed(ADMIN)
    @MutationMapping
    public String cleanupRadCheckDuplicates(
            @Argument @Valid @Min(value = 1, message = "From user ID must be positive") int fromUserId,
            @Argument @Valid @Min(value = 1, message = "To user ID must be positive") int toUserId) {
        log.info("Cleaning up RadCheck duplicates for users from ID {} to {}", fromUserId, toUserId);

        try {
            List<User> usersInRange = userService.getUsersByIdRange(fromUserId, toUserId);
            int processedCount = 0;
            int cleanedCount = 0;

            for (User user : usersInRange) {
                try {
                    processedCount++;
                    String username = user.getUsername();

                    List<RadCheck> passwordDuplicates = radCheckRepository.findByAttributeAndUsername("SHA-Password",
                            username);
                    List<RadCheck> simultaneousDuplicates = radCheckRepository
                            .findByAttributeAndUsername("Simultaneous-Use", username);
                    List<RadCheck> expirationDuplicates = radCheckRepository.findByAttributeAndUsername("Expiration",
                            username);

                    boolean hasDuplicates = passwordDuplicates.size() > 1 ||
                            simultaneousDuplicates.size() > 1 ||
                            expirationDuplicates.size() > 1;

                    if (hasDuplicates) {
                        radiusService.cleanupAllUserRadCheckDuplicates(username);
                        cleanedCount++;
                        log.info("Cleaned up duplicates for user: {}", username);
                    }

                } catch (Exception e) {
                    log.error("Failed to cleanup RadCheck duplicates for user {}: {}",
                            user.getUsername(), e.getMessage(), e);
                }
            }

            String result = String.format("RadCheck cleanup completed. Processed: %d users, Cleaned: %d users",
                    processedCount, cleanedCount);
            log.info(result);
            return result;

        } catch (Exception e) {
            log.error("Error during RadCheck cleanup: {}", e.getMessage(), e);
            throw e;
        }
    }

    @RolesAllowed(ADMIN)
    @MutationMapping
    public String getRadCheckDuplicateStats() {
        try {
            List<Object[]> duplicateStats = radCheckRepository.findDuplicateStats();

            StringBuilder result = new StringBuilder("RadCheck Duplicate Statistics:\n");
            int totalDuplicateUsers = 0;

            for (Object[] stat : duplicateStats) {
                String username = (String) stat[0];
                String attribute = (String) stat[1];
                Long count = (Long) stat[2];

                if (count > 1) {
                    result.append(String.format("User: %s, Attribute: %s, Count: %d\n",
                            username, attribute, count));
                    totalDuplicateUsers++;
                }
            }

            result.append(String.format("\nTotal users with duplicates: %d", totalDuplicateUsers));
            return result.toString();

        } catch (Exception e) {
            log.error("Error getting duplicate stats: {}", e.getMessage(), e);
            return "Error getting duplicate statistics: " + e.getMessage();
        }
    }
}