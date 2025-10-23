package com.orbvpn.api.service;

import com.orbvpn.api.domain.dto.*;
import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.mapper.*;
import com.orbvpn.api.repository.*;
import com.orbvpn.api.config.Messages;
import com.orbvpn.api.config.security.JwtTokenUtil;
import com.orbvpn.api.domain.enums.GatewayName;
import com.orbvpn.api.domain.enums.PaymentCategory;
import com.orbvpn.api.domain.enums.PaymentStatus;
import com.orbvpn.api.domain.enums.RoleName;
import com.orbvpn.api.event.UserActionEvent;
import com.orbvpn.api.event.UserDeletedEvent;
import com.orbvpn.api.exception.BadCredentialsException;
import com.orbvpn.api.exception.BadRequestException;
import com.orbvpn.api.exception.NotFoundException;
import com.orbvpn.api.service.notification.NotificationService;
import com.orbvpn.api.service.payment.PaymentUserService;
import com.orbvpn.api.service.reseller.ResellerService;
import com.orbvpn.api.service.webhook.WebhookEventCreator;
import com.orbvpn.api.service.webhook.WebhookService;
import com.orbvpn.api.service.subscription.UserSubscriptionService;
import com.orbvpn.api.utils.Utilities;

import graphql.GraphQLException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import java.util.*;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final UserCreateMapper userCreateMapper;
    private final UserViewMapper userViewMapper;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenUtil jwtTokenUtil;
    private final PasswordResetRepository passwordResetRepository;
    private final PaymentUserService paymentUserService;
    private final UserProfileRepository userProfileRepository;
    private final UserProfileEditMapper userProfileEditMapper;
    private final UserProfileViewMapper userProfileViewMapper;
    private final UserSubscriptionViewMapper userSubscriptionViewMapper;
    private final ReferralCodeRepository referralCodeRepository;
    private final RoleService roleService;
    private final ResellerService resellerService;
    private final GroupService groupService;
    private final UserSubscriptionService userSubscriptionService;
    private final PasswordService passwordService;
    private final ApplicationEventPublisher eventPublisher;
    private final WebhookService webhookService;
    private final WebhookEventCreator webhookEventCreator;
    private final ExtraLoginsHelper extraLoginsHelper;
    private final UserExtraLoginsRepository userExtraLoginsRepository;
    private final RadiusService radiusService;
    private final ExtraLoginsPlanRepository planRepository;
    private final UserUuidService userUuidService;
    private final ConnectionStatsTrackerService connectionStatsTrackerService;
    private final GeolocationService geolocationService;

    public AuthenticatedUser register(UserCreate userCreate) {
        return register(userCreate.getEmail(), userCreate.getPassword(), null);
    }

    public AuthenticatedUser register(String email, String password, String referral) {
        log.info("Creating user with data {}", email);

        Optional<User> userEntityOptional = userRepository.findByEmail(email);
        if (userEntityOptional.isPresent()) {
            throw new BadRequestException(Messages.getMessage("email_exists"));
        }

        UserCreate userCreateObj = new UserCreate(); // Avoid variable name conflict
        userCreateObj.setEmail(email);
        userCreateObj.setPassword(password);

        User user = userCreateMapper.createEntity(userCreateObj);
        user.setUsername(userCreateObj.getEmail());
        passwordService.setPassword(user, userCreateObj.getPassword());
        Role role = roleService.getByName(RoleName.USER);
        user.setRole(role);
        user.setReseller(resellerService.getOwnerReseller());
        UserProfile profile = new UserProfile();
        profile.setUser(user);
        user.setProfile(profile);

        // Save the user and flush to ensure it's persisted and ID is generated
        user = userRepository.saveAndFlush(user);

        // Verify the user has an ID before proceeding
        if (user.getId() == 0) {
            throw new RuntimeException("Failed to save user - no ID generated");
        }

        // Generate UUID using the properly saved user
        try {
            String uuid = userUuidService.getOrCreateUuid(user.getId());
            log.debug("Generated UUID {} for new user {}", uuid, user.getId());
            user.setUuid(uuid);
            user = userRepository.save(user);
        } catch (Exception e) {
            log.error("Failed to generate UUID for user {}: {}", user.getId(), e.getMessage(), e);
            // Continue without UUID - it can be generated later
            log.warn("Continuing user registration without UUID for user {}", user.getId());
        }

        if (referral != null && !referral.isEmpty()) {
            ReferralCode referralCode = referralCodeRepository.findReferralCodeByCode(referral);
            if (referralCode != null)
                referralCode.setInvitations(referralCode.getInvitations() + 1);
        }

        assignTrialSubscription(user);

        UserView userView = userViewMapper.toView(user);
        log.info("Created user {}", userView);
        // Add webhook event before returning
        webhookService.processWebhook("USER_CREATED",
                webhookEventCreator.createUserPayload(user, "USER_CREATED"));
        return loginInfo(user);
    }

    public List<User> createBulkUser(BulkUserCreate usersToCreate) {
        List<User> createdUsers = new ArrayList<>();

        Role role = roleService.getByName(RoleName.USER);
        List<User> users = usersToCreate.getUsers();
        List<UserProfile> profiles = usersToCreate.getProfiles();
        List<BulkSubscription> subscriptions = usersToCreate.getSubscriptions();

        for (int i = 0; i < users.size(); i++) {
            User user = users.get(i);
            UserProfile profile = profiles.get(i);
            BulkSubscription subscription = subscriptions.get(i);

            Optional<User> userEntityOptional = userRepository.findByEmail(user.getEmail());
            if (userEntityOptional.isPresent())
                continue;

            user.setUsername(user.getEmail());
            String password = user.getPassword();
            if (password == null || password.isEmpty())
                password = Utilities.getRandomPassword(10);

            passwordService.setPassword(user, password);
            user.setRole(role);
            user.setReseller(resellerService.getOwnerReseller());
            profile.setUser(user);
            user.setProfile(profile);

            userSubscriptionService.createBulkSubscription(user, subscription);
            userProfileRepository.save(profile);
            createdUsers.add(userRepository.save(user));
        }

        return createdUsers;
    }

    public User createUser(UserCreate userCreate) {

        Optional<User> oldUser = userRepository.findByEmail(userCreate.getEmail());
        if (oldUser.isPresent()) {
            throw new BadRequestException(Messages.getMessage("email_exists"));
        }

        User user = new User();
        webhookService.processWebhook("USER_CREATED",
                webhookEventCreator.createUserPayload(user, "USER_CREATED"));
        return userRepository.save(setUserInfo(user, userCreate));
    }

    public User updateUser(User user, UserCreate userCreate) {
        if (!user.getEmail().equals(userCreate.getEmail())) {
            Optional<User> oldUser = userRepository.findByEmail(userCreate.getEmail());
            if (oldUser.isPresent()) {
                throw new BadRequestException(Messages.getMessage("email_exists"));
            }
        }

        User newUser = userRepository.save(setUserInfo(user, userCreate));
        webhookService.processWebhook("USER_UPDATED",
                webhookEventCreator.createUserPayload(user, "USER_UPDATED"));
        return newUser;
    }

    public User setUserInfo(User user, UserCreate updatedInfo) {

        if (updatedInfo.getPassword() == null || updatedInfo.getPassword().isEmpty()) {
            String password = Utilities.getRandomPassword(10);
            updatedInfo.setPassword(password);
        }

        user.setEmail(updatedInfo.getEmail());
        user.setPassword(updatedInfo.getPassword());

        passwordService.setPassword(user, user.getPassword());
        user.setUsername(updatedInfo.getEmail());
        if (updatedInfo.getResellerId() != null) {
            user.setReseller(resellerService.getResellerById(updatedInfo.getResellerId()));
        }
        user.setRole(roleService.getByName(RoleName.USER));
        webhookService.processWebhook("USER_UPDATED",
                webhookEventCreator.createUserPayload(user, "USER_UPDATED"));
        return user;
    }

    public User createUserByAdmin(int resellerId, String email, String username, String password) {
        Optional<User> userEntityOptional = userRepository.findByEmail(email);
        if (userEntityOptional.isPresent()) {
            throw new BadRequestException(Messages.getMessage("email_exists"));
        }

        var user = new User();
        user.setEmail(email);
        user.setPassword(password);
        passwordService.setPassword(user, password);
        user.setUsername(username);
        if (resellerId != 0) {
            var reseller = resellerService.getResellerById(resellerId);
            user.setReseller(reseller);
        }
        user.setRole(roleService.getByName(RoleName.USER));
        var profile = new UserProfile();
        profile.setUser(user);
        user.setProfile(profile);

        // Save and flush to ensure the user gets an ID
        user = userRepository.saveAndFlush(user);

        // Generate UUID for the user
        try {
            String uuid = userUuidService.getOrCreateUuid(user.getId());
            log.debug("Generated UUID {} for admin-created user {}", uuid, user.getId());
            user.setUuid(uuid);
            user = userRepository.save(user);
        } catch (Exception e) {
            log.error("Failed to generate UUID for admin-created user {}: {}", user.getId(), e.getMessage(), e);
            // Continue without UUID - it can be generated later
            log.warn("Continuing user creation without UUID for user {}", user.getId());
        }

        webhookService.processWebhook("USER_CREATED",
                webhookEventCreator.createUserPayload(user, "USER_CREATED"));
        return user;
    }

    public void assignTrialSubscription(User user) {
        // Assign trial group
        Group group = groupService.getById(1);
        String paymentId = UUID.randomUUID().toString();
        Payment payment = Payment.builder()
                .user(user)
                .status(PaymentStatus.PENDING)
                .gateway(GatewayName.FREE)
                .category(PaymentCategory.GROUP)
                .price(group.getPrice())
                .groupId(group.getId())
                .paymentId(paymentId)
                .build();
        paymentUserService.savePayment(payment); // Use new service

    }

    public User getUserFromAuthentication(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new BadCredentialsException("Invalid authentication");
        }

        if (authentication.getPrincipal() instanceof User) {
            return (User) authentication.getPrincipal();
        }

        // If principal is username string, fetch user from repository
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    public String generateRandomString() {
        int length = 10;
        return RandomStringUtils.random(length, true, true);
    }

    public String generateVerificationCode() {
        return String.format("%06d", new Random().nextInt(999999));
    }

    public AuthenticatedUser login(String email, String password) {
        log.info("Authenticating user with email {}", email);

        // Step 1: Check if the user exists by email
        Optional<User> optionalUser = userRepository.findByEmailAndActiveTrue(email);
        if (optionalUser.isEmpty()) {
            log.warn("Authentication failed: No user found with email {} or account inactive", email);
            throw new BadCredentialsException("Invalid email or inactive account");
        }

        User user = optionalUser.get();
        log.debug("User found: ID={}, Email={}, Active={}", user.getId(), user.getEmail(), user.isActive());

        // Step 2: Validate password using AES/Bcrypt depending on what's stored
        boolean passwordMatches = false;

        if (user.getAesKey() != null && user.getAesIv() != null) {
            log.debug("AES decryption will be attempted for user ID={}", user.getId());
            try {
                String decryptedPassword = passwordService.getPassword(user);
                log.trace("Decrypted password for user ID={} is: {}", user.getId(), decryptedPassword); // Be cautious
                                                                                                        // about logging
                                                                                                        // sensitive
                                                                                                        // data.
                if (decryptedPassword.equals(password)) {
                    passwordMatches = true;
                } else {
                    log.warn("Password mismatch for user ID={} using AES decryption", user.getId());
                }
            } catch (Exception e) {
                log.error("Error during AES password decryption for user ID={}", user.getId(), e);
            }
        } else {
            log.debug("Bcrypt password matching will be attempted for user ID={}", user.getId());
            passwordMatches = passwordEncoder.matches(password, user.getPassword());
            if (!passwordMatches) {
                log.warn("Password mismatch for user ID={} using Bcrypt", user.getId());
            }
        }

        // Step 3: If password doesn't match, throw error
        if (!passwordMatches) {
            log.error("Authentication failed: Invalid password for user ID={}", user.getId());
            throw new BadCredentialsException("Invalid password");
        }

        log.info("Authentication successful for user ID={}, Email={}", user.getId(), user.getEmail());

        // After successful password validation and before returning
        try {
            webhookService.processWebhook("USER_LOGIN",
                    webhookEventCreator.createUserPayload(user, "USER_LOGIN"));
            log.info("Webhook invoked for event: USER_LOGIN, User ID={}", user.getId());
        } catch (Exception e) {
            log.error("Error invoking webhook for USER_LOGIN event for user ID={}", user.getId(), e);
        }

        // Step 4: If authentication passes, return user info
        return loginInfo(user);
    }

public AuthenticatedUser loginInfo(User user) {
    if (user == null) {
        log.error("User is null in loginInfo method");
        throw new GraphQLException("User cannot be null");
    }

    log.debug("Mapping user to UserView for user ID={}", user.getId());

    // Update user's IP and country BEFORE generating token
    updateUserLocation(user);

    // ✅ IMPORTANT: Fetch user with subscriptions to include in JWT
    User userWithDetails = userRepository.findByIdWithDetails(user.getId())
            .orElse(user);

    UserView userView = userViewMapper.toView(userWithDetails);

    log.debug("Generating JWT tokens for user ID={}", user.getId());
    
    // ✅ UPDATED: Pass User entity (not UserDetails) to include all claims
    String accessToken = jwtTokenUtil.generateAccessToken(userWithDetails);
    String refreshToken = jwtTokenUtil.generateRefreshToken(userWithDetails);

    if (accessToken == null) {
        log.error("Failed to generate access token for user ID={}", user.getId());
        throw new GraphQLException("Token generation failed");
    }

    log.info("Successfully generated tokens for user ID={} with full claims", user.getId());

    return new AuthenticatedUser(accessToken, refreshToken, userView);
}

    public boolean requestResetPassword(String email) {
        log.info("Resetting password for user: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User with specified email not exists"));

        String token = generateVerificationCode();

        PasswordReset passwordReset = new PasswordReset();
        passwordReset.setUser(user);
        passwordReset.setToken(token);
        passwordResetRepository.save(passwordReset);

        notificationService.resetPassword(user, token);
        passwordResetRepository.deleteByUserAndTokenNot(user, token);
        return true;
    }

    public boolean resetPassword(String token, String password) {
        log.info("Resetting password for token: {}", token);

        PasswordReset passwordReset = passwordResetRepository.findById(token)
                .orElseThrow(() -> new NotFoundException("Token was not found"));

        User user = passwordReset.getUser();

        // Set the new password (this updates user.password, user.radAccess, and calls
        // radiusService.editUserPassword)
        passwordService.setPassword(user, password);

        // Save the user
        userRepository.save(user);

        // Ensure full password synchronization across all systems
        try {
            radiusService.synchronizeUserPassword(user);
            log.info("Password synchronization completed for user: {}", user.getUsername());
        } catch (Exception e) {
            log.error("Password synchronization failed for user: {} - Error: {}", user.getUsername(), e.getMessage(),
                    e);
            // Still continue with the process as the main password was set
        }

        // Clean up password reset token
        passwordResetRepository.delete(passwordReset);

        // Send notification
        notificationService.resetPasswordDone(user);

        // Publish events
        eventPublisher.publishEvent(new UserActionEvent(this, user, "PASSWORD_RESET"));
        webhookService.processWebhook("PASSWORD_RESET",
                webhookEventCreator.createUserPayload(user, "PASSWORD_RESET"));

        return true;
    }

    // Replace the existing changePassword method in UserService
    public boolean changePassword(int id, String oldPassword, String newPassword) {
        log.info("Changing password for user with id {}", id);

        // Step 1: Retrieve the user
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));

        // Step 2: Validate the old password using AES/Bcrypt
        String storedPassword = user.getPassword();
        boolean passwordMatches = false;

        if (user.getAesKey() != null && user.getAesIv() != null) {
            // Use AES for decryption
            try {
                String decryptedPassword = passwordService.getPassword(user);
                if (decryptedPassword.equals(oldPassword)) {
                    passwordMatches = true;
                }
            } catch (Exception e) {
                log.error("Error during AES password decryption", e);
            }
        } else {
            // Use BCrypt to check the password
            passwordMatches = passwordEncoder.matches(oldPassword, storedPassword);
        }

        // Step 3: If the old password doesn't match, throw an error
        if (!passwordMatches) {
            throw new BadRequestException("Wrong password");
        }

        // Step 4: Set the new password (this will update user.password, user.radAccess)
        passwordService.setPassword(user, newPassword);

        // Save the user
        userRepository.save(user);

        // Step 5: Ensure full password synchronization across all systems
        try {
            radiusService.synchronizeUserPassword(user);
            log.info("Password synchronization completed for user: {}", user.getUsername());
        } catch (Exception e) {
            log.error("Password synchronization failed for user: {} - Error: {}",
                    user.getUsername(), e.getMessage(), e);
            // Continue as the main password was changed successfully
        }

        // Publish events
        eventPublisher.publishEvent(new UserActionEvent(this, user, "PASSWORD_CHANGED"));
        webhookService.processWebhook("PASSWORD_CHANGED",
                webhookEventCreator.createUserPayload(user, "PASSWORD_CHANGED"));

        return true;
    }

    // Replace the existing reEncryptPasswordsInRange method in UserService
    public void reEncryptPasswordsInRange(int fromUserId, int toUserId, boolean sendEmail) {
        List<User> usersInRange = userRepository.findByIdBetween(fromUserId, toUserId);

        log.info("Starting password re-encryption for {} users in range {} to {}",
                usersInRange.size(), fromUserId, toUserId);

        for (User user : usersInRange) {
            try {
                if (StringUtils.isEmpty(user.getPassword())) {
                    log.info("Skipping user without password: {}", user.getEmail());
                    continue;
                }

                // Check if user needs password re-encryption or RadCheck synchronization
                boolean needsReEncryption = StringUtils.isEmpty(user.getAesKey())
                        || StringUtils.isEmpty(user.getAesIv());
                boolean needsRadCheckSync = !radiusService.hasValidRadCheckPassword(user);

                if (needsReEncryption) {
                    log.info("Re-encrypting password for user with missing or empty encryption keys: {}",
                            user.getEmail());

                    String newPassword = generateRandomPassword();

                    // Set the new password (this will update user.password, user.radAccess)
                    passwordService.setPassword(user, newPassword);

                    // Save user to the database
                    userRepository.save(user);

                    // Ensure full password synchronization across all systems
                    try {
                        radiusService.synchronizeUserPassword(user);
                        log.info("Password re-encryption and synchronization completed for user: {}", user.getEmail());
                    } catch (Exception e) {
                        log.error("Password synchronization failed for user: {} - Error: {}",
                                user.getEmail(), e.getMessage(), e);
                        // Continue with other users even if sync fails for this one
                    }

                    // Publish events
                    eventPublisher.publishEvent(new UserActionEvent(this, user, "PASSWORD_REENCRYPTED"));
                    webhookService.processWebhook("PASSWORD_REENCRYPTED",
                            webhookEventCreator.createUserPayload(user, "PASSWORD_REENCRYPTED"));

                    if (sendEmail) {
                        sendEmailNotification(user, newPassword);
                    }
                } else if (needsRadCheckSync) {
                    log.info(
                            "Synchronizing RadCheck password for user with valid encryption but missing/invalid RadCheck: {}",
                            user.getEmail());

                    try {
                        // Just synchronize the existing password without generating a new one
                        radiusService.synchronizeUserPassword(user);
                        log.info("RadCheck synchronization completed for user: {}", user.getEmail());
                    } catch (Exception e) {
                        log.error("RadCheck synchronization failed for user: {} - Error: {}",
                                user.getEmail(), e.getMessage(), e);
                    }

                    // Publish event for sync
                    eventPublisher.publishEvent(new UserActionEvent(this, user, "PASSWORD_SYNCHRONIZED"));
                    webhookService.processWebhook("PASSWORD_SYNCHRONIZED",
                            webhookEventCreator.createUserPayload(user, "PASSWORD_SYNCHRONIZED"));
                } else {
                    log.info("Skipping user with valid encryption keys and valid RadCheck: {}", user.getEmail());
                }
            } catch (Exception e) {
                log.error("Error processing password for user: {}", user.getEmail(), e);
                throw e; // Ensure the exception marks the transaction for rollback
            }
        }

        log.info("Completed password re-encryption for users in range {} to {}", fromUserId, toUserId);
    }

    private void sendEmailNotification(User user, String newPassword) {
        try {
            notificationService.notifyUserPasswordReEncryption(user, newPassword);
            log.info("Successfully notified user: {}", user.getEmail());

            Thread.sleep(4000); // Add delay to avoid hitting rate limits
        } catch (Exception e) {
            log.error("Error sending email for user: {}", user.getEmail(), e);
        }
    }

    private String generateRandomPassword() {
        int length = 12; // Choose your desired password length
        return RandomStringUtils.random(length, true, true); // Generates an alphanumeric password
    }

    /**
     * Delete user and the whole dependant entities including:
     * userProfile, reseller, resellerAddCredit, userSubscription, PasswordReset,
     * Payment, radaacct, radcheck
     * <p>
     * these not finalized entities are not checked yet : StripeCustomer, Ticket,
     * MoreLoginCount, OathToken, TicketReply
     *
     * @param user: user for deletion
     */

    public void softDeleteUser(User user) {
        user.setActive(false);
        userRepository.save(user);
        log.info("Soft-deleted user with ID: {}", user.getId());

        // Optionally, publish a soft delete event
        eventPublisher.publishEvent(new UserActionEvent(this, user, "USER_SOFT_DELETED"));
        webhookService.processWebhook("USER_SOFT_DELETED",
                webhookEventCreator.createUserPayload(user, "USER_SOFT_DELETED"));
    }

    // public User deleteOauthUser(String oauthId) {
    // User user = userRepository.findByOauthId(oauthId)
    // .orElseThrow(() -> new NotFoundException("User not found"));

    // deleteUser(user);
    // return user;
    // }

    // Method to delete a user (already refactored above)
    @Transactional
    public void deleteUser(User user) {
        try {
            log.info("Starting deletion process for user ID: {}", user.getId());

            // First, perform all cleanup operations
            // userCleanupService.dissociateAndCleanup(user);

            // Then delete the user
            userRepository.delete(user);
            userRepository.flush(); // Force the delete operation

            // Publish events after successful deletion
            eventPublisher.publishEvent(new UserDeletedEvent(this, user));
            eventPublisher.publishEvent(new UserActionEvent(this, user, "USER_DELETED"));
            webhookService.processWebhook("USER_DELETED",
                    webhookEventCreator.createUserPayload(user, "USER_DELETED"));

            log.info("Successfully deleted user with ID: {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to delete user ID: {} - Error: {}", user.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to delete user", e);
        }
    }

    // @Transactional
    // public void forceDeleteUser(User user) {
    // try {
    // log.warn("Attempting force delete for user ID: {}", user.getId());

    // // Disable foreign key checks
    // entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS =
    // 0").executeUpdate();

    // try {
    // // Force delete all related data
    // userRepository.forceDelete(user.getId());

    // log.info("Force deleted user ID: {}", user.getId());
    // } finally {
    // // Re-enable foreign key checks
    // entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS =
    // 1").executeUpdate();
    // }

    // // Verify deletion
    // if (userRepository.existsById(user.getId())) {
    // throw new RuntimeException("Failed to delete user even with force delete");
    // }
    // } catch (Exception e) {
    // log.error("Critical error during force delete of user ID: {}", user.getId(),
    // e);
    // throw new RuntimeException("Failed to force delete user", e);
    // }
    // }

    // Method for a user to delete its own account
    @Transactional
    public boolean deleteUserAccount(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));

        log.info("Initiating deletion process for user with ID: {}", user.getId());

        // Delegate dissociation and cleanup to UserCleanupService via events
        deleteUser(user);

        // Publish the event
        eventPublisher.publishEvent(new UserActionEvent(this, user, "USER_ACCOUNT_DELETED"));
        webhookService.processWebhook("USER_ACCOUNT_DELETED",
                webhookEventCreator.createUserPayload(user, "USER_ACCOUNT_DELETED"));

        return true;
    }

    public UserProfileView editProfile(UserProfileEdit userProfileEdit) {
        User user = getUser();

        log.info("Editing user{} profile{}", user.getId(), userProfileEdit);

        UserProfile userProfile = userProfileRepository.findByUser(user).orElse(new UserProfile());

        UserProfile edited = userProfileEditMapper.edit(userProfile, userProfileEdit);
        edited.setUser(user);

        userProfileRepository.save(edited);

        // Add webhook event before returning
        webhookService.processWebhook("PROFILE_UPDATED",
                webhookEventCreator.createUserPayload(user, "PROFILE_UPDATED"));

        return userProfileViewMapper.toView(edited);
    }

    public UserProfileView editProfileByAdmin(User user, UserProfileEdit userProfileEdit) {
        log.info("Editing user{} profile{}", user.getId(), userProfileEdit);
        UserProfile userProfile = userProfileRepository.findByUser(user).orElse(new UserProfile());
        UserProfile edited = userProfileEditMapper.edit(userProfile, userProfileEdit);
        edited.setUser(user);
        userProfileRepository.save(edited);
        return userProfileViewMapper.toView(edited);
    }

    public UserProfileView getProfile() {
        User user = getUser();

        UserProfile userProfile = userProfileRepository.findByUser(user).orElse(new UserProfile());

        return userProfileViewMapper.toView(userProfile);
    }

    public UserSubscriptionView getUserSubscription() {
        User user = getUser();
        UserSubscription currentSubscription = userSubscriptionService.getCurrentSubscription(user);
        return userSubscriptionViewMapper.toView(currentSubscription);
    }

    public UserSubscriptionView getUserSubscription(User user) {
        UserSubscription currentSubscription = userSubscriptionService.getCurrentSubscription(user);
        return userSubscriptionViewMapper.toView(currentSubscription);
    }

    public List<UserDeviceInfo> getUserDeviceInfo() {
        User user = getUser();
        String username = user.getUsername();

        List<String> allDevices = userRepository.findAllUserDevices(username);
        List<String> allActiveDevices = userRepository.findAllActiveUserDevices(username);

        return allDevices.stream()
                .filter(StringUtils::isNoneBlank)
                .map(s -> {
                    UserDeviceInfo userDeviceInfo = new UserDeviceInfo();
                    userDeviceInfo.setName(s);
                    userDeviceInfo.setActive(allActiveDevices.contains(s));
                    return userDeviceInfo;
                }).collect(Collectors.toList());
    }

    public User getUserById(int id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(User.class, id));
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmailWithSubscription(email)
                .orElseThrow(() -> new NotFoundException("User not found with email: " + email));
    }

    public User getUserByIdWithSubscription(int id) {
        return userRepository.findByIdWithSubscriptionChain(id)
                .orElseThrow(() -> new NotFoundException(User.class, id));
    }

    public User getUserByEmailWithSubscription(String email) {
        return userRepository.findByEmailWithSubscriptionChain(email)
                .orElseThrow(() -> new NotFoundException(User.class, email));
    }

    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException(User.class, username));
    }

    public User getUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (User) authentication.getPrincipal();
    }

    public Role getUserRole() {
        User user = getUser();
        return user.getRole();
    }

    public boolean isAdmin() {
        User user = getUser();
        return user.getRole().getName() == RoleName.ADMIN;
    }

    @Transactional(readOnly = true)
    public UserView getUserView() {
        User user = getUser();
        return getUserFullView(user);
    }

    @Transactional(readOnly = true)
    public UserView getUserFullView(User user) {
        // Fetch user with all subscriptions using the existing repository method
        User userWithSubscriptions = userRepository.findByIdWithSubscriptionChain(user.getId())
                .orElseThrow(() -> new NotFoundException(User.class, user.getId()));

        // Map to UserView
        UserView userView = userViewMapper.toView(userWithSubscriptions);

        // Set device info if needed
        try {
            userView.setUserDevicesInfo(getUserDeviceInfo());
        } catch (Exception e) {
            log.warn("Could not fetch device info for user {}: {}", user.getId(), e.getMessage());
            // Continue without device info
        }

        return userView;
    }

    public Boolean editAutoRenew(Boolean isActive) {
        User user = getUser();
        user.setAutoRenew(isActive);
        userRepository.save(user);

        // Add webhook event before returning
        webhookService.processWebhook("AUTO_RENEW_UPDATED",
                webhookEventCreator.createUserPayload(user, "AUTO_RENEW_UPDATED"));

        return true;
    }

    public int getActiveUserCountOfReseller(int resellerId) {
        return userRepository.getActiveUsersOfReseller(resellerId);
    }

    @Transactional
    public User save(User user) {
        log.debug("Saving user: {}", user.getEmail());

        // Validate the user object
        if (user.getEmail() == null || user.getEmail().isEmpty()) {
            throw new IllegalArgumentException("User email cannot be empty");
        }

        // Save the user
        User savedUser = userRepository.save(user);

        // Handle any extra logins the user might have
        int totalExtraLogins = userExtraLoginsRepository.getTotalActiveLoginCount(savedUser);
        if (totalExtraLogins > 0) {
            radiusService.updateUserTotalLoginCount(savedUser);
        }

        log.debug("Successfully saved user: {}", savedUser.getEmail());
        webhookService.processWebhook("USER_UPDATED",
                webhookEventCreator.createUserPayload(savedUser, "USER_UPDATED"));

        return savedUser;
    }

    @Transactional
    public void updateUserLoginCount(User user, int newLoginCount) {
        log.info("Updating login count for user: {} to {}", user.getUsername(), newLoginCount);

        try {
            // Get current subscription
            UserSubscription currentSubscription = userSubscriptionService.getCurrentSubscription(user);
            if (currentSubscription != null) {
                // Update the subscription's multi-login count
                currentSubscription.setMultiLoginCount(newLoginCount);
                userSubscriptionService.saveUserSubscription(currentSubscription);
            }

            // Update in radius service
            radiusService.updateUserTotalLoginCount(user);

            log.info("Successfully updated login count for user: {}", user.getUsername());

            // Publish event for the update
            eventPublisher.publishEvent(new UserActionEvent(this, user, "LOGIN_COUNT_UPDATED"));
            webhookService.processWebhook("LOGIN_COUNT_UPDATED",
                    webhookEventCreator.createUserPayload(user, "LOGIN_COUNT_UPDATED"));

        } catch (Exception e) {
            log.error("Failed to update login count for user: {}", user.getUsername(), e);
            throw new RuntimeException("Failed to update user login count", e);
        }
    }

    /**
     * Add extra logins to a user
     */
    @Transactional
    public void addExtraLogins(User user, Long planId, int quantity) {
        ExtraLoginsPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new NotFoundException("Extra logins plan not found"));

        extraLoginsHelper.addExtraLogins(user, plan, quantity);

        // Update the total login count
        updateUserLoginCount(user, userExtraLoginsRepository.getTotalActiveLoginCount(user));
    }

    /**
     * Remove extra logins from a user
     */
    @Transactional
    public void removeExtraLogins(User user, Long extraLoginsId) {
        extraLoginsHelper.removeExtraLogins(user, extraLoginsId);

        // Update the total login count
        updateUserLoginCount(user, userExtraLoginsRepository.getTotalActiveLoginCount(user));
    }

    @Transactional(readOnly = true)
    public User getUserByEmailWithUuid(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found with email: " + email));
        userUuidService.ensureUuidExists(user);
        return user;
    }

    @Transactional(readOnly = true)
    public User getCurrentUserWithUuid() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new BadCredentialsException("No authenticated user found");
        }

        User user;
        if (authentication.getPrincipal() instanceof User) {
            user = (User) authentication.getPrincipal();
        } else {
            user = userRepository.findByUsername(authentication.getName())
                    .orElseThrow(() -> new NotFoundException("User not found"));
        }

        userUuidService.ensureUuidExists(user);
        return user;
    }

    @Transactional
    public void handleUserConnection(User user, MiningServer server, boolean isConnecting) {
        if (isConnecting) {
            connectionStatsTrackerService.startTracking(user, server);
        } else {
            connectionStatsTrackerService.endTracking(user);
        }
    }

    public List<User> getUsersByIdRange(int fromUserId, int toUserId) {
        return userRepository.findByIdBetween(fromUserId, toUserId);
    }

    /**
     * Update user's IP address and country from current request
     */
    @Transactional
    public void updateUserLocation(User user) {
        try {
            String currentIp = geolocationService.getCurrentUserIP();

            if (currentIp != null && !currentIp.equals(user.getLastKnownIp())) {
                // IP has changed, update it
                user.setLastKnownIp(currentIp);
                user.setIpUpdatedAt(LocalDateTime.now());

                // Try to determine country from IP
                String countryCode = geolocationService.getCountryCodeFromIP(currentIp);
                if (countryCode != null && user.getCountry() == null) {
                    // Only set if user hasn't manually set their country
                    user.setCountry(countryCode);
                    log.info("Updated user {} country to {} based on IP {}",
                            user.getId(), countryCode, currentIp);
                }

                userRepository.save(user);
                log.debug("Updated user {} IP to {}", user.getId(), currentIp);
            }

        } catch (Exception e) {
            log.error("Error updating user location", e);
            // Don't fail login if location update fails
        }
    }
}