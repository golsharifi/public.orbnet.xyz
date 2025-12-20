package com.orbvpn.api.service.reseller;

import com.orbvpn.api.config.LocaleConfig;
import com.orbvpn.api.domain.dto.AdminPasswordResetResult;
import com.orbvpn.api.domain.dto.ResellerUserCreate;
import com.orbvpn.api.domain.dto.ResellerUserEdit;
import com.orbvpn.api.domain.dto.UserView;
import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.domain.enums.*;
import com.orbvpn.api.exception.BadRequestException;
import com.orbvpn.api.exception.InsufficientFundsException;
import com.orbvpn.api.exception.NotFoundException;
import com.orbvpn.api.mapper.ResellerUserCreateMapper;
import com.orbvpn.api.mapper.UserProfileEditMapper;
import com.orbvpn.api.mapper.UserSubscriptionViewMapper;
import com.orbvpn.api.mapper.UserViewMapper;
import com.orbvpn.api.repository.PaymentRepository;
import com.orbvpn.api.repository.ResellerAddCreditRepository;
import com.orbvpn.api.repository.ResellerRepository;
import com.orbvpn.api.repository.UserProfileRepository;
import com.orbvpn.api.repository.UserRepository;
import com.orbvpn.api.service.subscription.UserSubscriptionService;
import com.orbvpn.api.service.UserService;
import com.orbvpn.api.service.AsyncNotificationHelper;
import com.orbvpn.api.service.GroupService;
import com.orbvpn.api.service.RoleService;
import com.orbvpn.api.service.PasswordService;
import com.orbvpn.api.service.payment.PaymentService;
import com.orbvpn.api.utils.Utilities;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static com.orbvpn.api.config.AppConstants.DEFAULT_SORT;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ResellerUserService {

    private final ResellerUserCreateMapper resellerUserCreateMapper;
    private final UserViewMapper userViewMapper;
    private final UserProfileEditMapper userProfileEditMapper;
    private final PasswordService passwordService;
    private final RoleService roleService;
    private final GroupService groupService;
    private final UserSubscriptionService userSubscriptionService;
    private final ResellerService resellerService;
    private final ResellerSaleService resellerSaleService;
    private final UserRepository userRepository;
    private final ResellerRepository resellerRepository;
    private final ResellerAddCreditRepository resellerAddCreditRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final AsyncNotificationHelper asyncNotificationHelper;
    private final UserSubscriptionViewMapper userSubscriptionViewMapper;
    private final UserProfileRepository userProfileRepository;

    @Lazy
    private final UserService userService;

    public UserView createUser(ResellerUserCreate resellerUserCreate) {
        log.info("Creating user");
        User creator = userService.getUser();
        Reseller reseller = creator.getReseller();
        Group group = groupService.getById(resellerUserCreate.getGroupId());

        log.debug("Checking for existing user");
        Optional<User> userEntityOptional = userRepository.findByEmail(resellerUserCreate.getEmail());
        if (userEntityOptional.isPresent()) {
            throw new BadRequestException("User with specified email exists");
        }

        log.debug("Creating new user entity");
        User user = resellerUserCreateMapper.create(resellerUserCreate);
        String username = resellerUserCreate.getUserName();
        String password = resellerUserCreate.getPassword();

        if (username == null || username.equals(""))
            user.setUsername(resellerUserCreate.getEmail());
        else
            user.setUsername(username);

        if (password == null || password.equals("")) {
            var randomPassword = Utilities.getRandomPassword(10);
            resellerUserCreate.setPassword(randomPassword);
        }
        passwordService.setPassword(user, resellerUserCreate.getPassword());

        Role role = roleService.getByName(RoleName.USER);
        user.setRole(role);
        user.setReseller(reseller);

        // Generate UUID before saving to avoid transaction issues
        String uuid = UUID.randomUUID().toString();
        user.setUuid(uuid);
        log.debug("Generated UUID {} for new user", uuid);

        UserProfile profile = new UserProfile();
        profile.setUser(user);
        profile.setFirstName(resellerUserCreate.getFirstName());
        profile.setLastName(resellerUserCreate.getLastName());
        profile.setCountry(resellerUserCreate.getCountry());
        profile.setPhone(resellerUserCreate.getPhone());
        profile.setTelegramUsername(resellerUserCreate.getTelegramUsername());
        profile.setTelegramChatId(resellerUserCreate.getTelegramChatId());
        profile.setLanguage(resellerUserCreate.getLanguage() != null
                ? resellerUserCreate.getLanguage()
                : LocaleConfig.DEFAULT_LOCALE);
        user.setProfile(profile);

        log.debug("Saving user to database with UUID");
        user = userRepository.saveAndFlush(user); // Use saveAndFlush to ensure immediate persistence

        // Verify the user was saved successfully
        if (user.getId() == 0) {
            throw new RuntimeException("Failed to save user - no ID generated");
        }

        log.info("Successfully created user with ID: {} and UUID: {}", user.getId(), user.getUuid());

        createResellerUserSubscription(user, group);
        if (resellerUserCreate.getLogin() > 0) {
            userSubscriptionService.updateSubscriptionMultiLoginCount(user, resellerUserCreate.getLogin());
        }
        UserView userView = userViewMapper.toView(user);

        // Refresh the user entity to ensure we have the latest data
        user = userRepository.findById(user.getId()).orElseThrow();

        UserSubscription sub = userSubscriptionService.getCurrentSubscription(user);
        userView.setCurrentSubscription(sub);

        // Force a commit of the transaction
        TransactionAspectSupport.currentTransactionStatus().flush();

        // Send notifications asynchronously to avoid blocking
        asyncNotificationHelper.sendWelcomeEmailWithSubscriptionAsync(user, sub, resellerUserCreate.getPassword());
        asyncNotificationHelper.sendUserWebhookAsync(user, "USER_CREATED");

        log.info("Created user");
        return userView;
    }

    @Transactional
    public void createResellerUserSubscription(User user, Group group) {
        log.info("Creating reseller subscription for user {} with group {}", user.getId(), group.getId());

        try {
            // Lock reseller to prevent race condition on credit deduction
            Reseller reseller = resellerRepository.findByIdWithLock(user.getReseller().getId())
                    .orElseThrow(() -> new RuntimeException("Reseller not found"));

            BigDecimal credit = reseller.getCredit();
            BigDecimal price = calculatePrice(reseller, group);

            if (credit.compareTo(price) < 0) {
                throw new InsufficientFundsException();
            }

            // Update reseller credit (atomic with check due to lock)
            BigDecimal newBalance = credit.subtract(price);
            reseller.setCredit(newBalance);
            resellerRepository.save(reseller);

            // Record credit transaction for audit trail
            ResellerAddCredit creditTransaction = ResellerAddCredit.createUserPurchase(
                    reseller, price, newBalance, user.getId(), user.getEmail());
            resellerAddCreditRepository.save(creditTransaction);

            // Create and save payment first
            String paymentId = UUID.randomUUID().toString();
            Payment payment = Payment.builder()
                    .user(user)
                    .status(PaymentStatus.PENDING)
                    .gateway(GatewayName.RESELLER_CREDIT)
                    .category(PaymentCategory.GROUP)
                    .price(group.getPrice())
                    .groupId(group.getId())
                    .paymentId(paymentId)
                    .build();

            // Save payment first
            payment = paymentRepository.save(payment);
            paymentRepository.flush();

            // Create sale record
            resellerSaleService.createSale(reseller, user, group, price);

            // Now fulfill the payment
            paymentService.fullFillPayment(payment);

        } catch (Exception e) {
            log.error("Error creating reseller subscription - User: {} Group: {} - Error: {}",
                    user.getId(), group.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to create reseller subscription", e);
        }
    }

    public BigDecimal calculatePrice(Reseller reseller, Group group) {
        ResellerLevel level = reseller.getLevel();
        if (level.getName().equals(ResellerLevelName.OWNER)) {
            return BigDecimal.ZERO;
        }

        BigDecimal price = group.getPrice();
        BigDecimal discount = price.multiply(level.getDiscountPercent()).divide(new BigDecimal(100));

        return price.subtract(discount);
    }

    public UserView editUserByEmail(String email, ResellerUserEdit resellerUserEdit) {
        log.info("Starting edit for user with email: {}", email);

        // First, get just the user ID using a simple query
        User baseUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found with email: " + email));

        // If we're only updating profile information, use a simplified path
        if (isOnlyProfileUpdate(resellerUserEdit)) {
            UserProfile profile = userProfileRepository.findByUser(baseUser)
                    .orElseGet(() -> {
                        UserProfile newProfile = new UserProfile();
                        newProfile.setUser(baseUser);
                        return newProfile;
                    });

            userProfileEditMapper.edit(profile, resellerUserEdit.getUserProfileEdit());
            userProfileRepository.save(profile);

            // Fetch fresh user view with updated data
            return getUserView(baseUser.getId());
        }

        // For other updates, use the full user fetch
        return editUser(baseUser, resellerUserEdit);
    }

    private boolean isOnlyProfileUpdate(ResellerUserEdit resellerUserEdit) {
        return resellerUserEdit.getUserProfileEdit() != null
                && resellerUserEdit.getMultiLoginCount() != null // Check for multiLoginCount
                && resellerUserEdit.getPassword() == null
                && resellerUserEdit.getResellerId() == null
                && resellerUserEdit.getGroupId() == null;
    }

    private UserView getUserView(int userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return userViewMapper.toView(user);
    }

    public UserView editUserById(int id, ResellerUserEdit resellerUserEdit) {
        User user = userService.getUserById(id);
        return editUser(user, resellerUserEdit);
    }

    @Transactional
    public UserView editUser(User user, ResellerUserEdit resellerUserEdit) {
        log.info("Starting edit for user {}", user.getId());
        checkResellerUserAccess(user);

        if (resellerUserEdit == null) {
            log.warn("ResellerUserEdit is null for user {}", user.getId());
            return userViewMapper.toView(user);
        }

        try {
            // Password update
            String password = resellerUserEdit.getPassword();
            if (password != null) {
                passwordService.setPassword(user, password);
                log.debug("Updated password for user {}", user.getId());
            }

            // Reseller update
            Integer resellerId = resellerUserEdit.getResellerId();
            if (resellerId != null && userService.isAdmin()) {
                Reseller reseller = resellerService.getResellerById(resellerId);
                user.setReseller(reseller);
                log.debug("Updated reseller to {} for user {}", resellerId, user.getId());
            }

            // Group update
            Integer groupId = resellerUserEdit.getGroupId();
            if (groupId != null) {
                Group group = groupService.getById(groupId);
                createResellerUserSubscription(user, group);
                log.debug("Created new subscription with group {} for user {}", groupId, user.getId());
            }

            // MultiLogin update with charging
            Integer multiLoginCount = resellerUserEdit.getMultiLoginCount();
            if (multiLoginCount != null) {
                log.debug("Updating multiLoginCount to {} for user {}", multiLoginCount, user.getId());
                updateUserDeviceCountWithCharging(user, multiLoginCount);
            }

            // Profile updates
            if (user.getProfile() == null) {
                UserProfile profile = new UserProfile();
                profile.setUser(user);
                user.setProfile(profile);
                log.debug("Created new profile for user {}", user.getId());
            }

            if (resellerUserEdit.getUserProfileEdit() != null) {
                userProfileEditMapper.edit(user.getProfile(), resellerUserEdit.getUserProfileEdit());
                log.debug("Updated profile for user {} - city: {}", user.getId(), user.getProfile().getCity());
            }

            // Refresh user to get latest state
            user = userRepository.findById(user.getId())
                    .orElseThrow(() -> new RuntimeException("Failed to retrieve updated user"));

            // Get current subscription with null check
            UserSubscription currentSubscription = userSubscriptionService.getCurrentSubscription(user);
            UserProfile profile = user.getProfile();

            UserView userView = userViewMapper.toView(user);

            log.info("Successfully edited user {} - Final state: [multiLoginCount={}, resellerId={}, groupId={}, " +
                    "city={}, country={}, firstName={}, lastName={}, phone={}]",
                    user.getId(),
                    currentSubscription != null ? currentSubscription.getMultiLoginCount() : null,
                    user.getReseller() != null ? user.getReseller().getId() : null,
                    currentSubscription != null ? currentSubscription.getGroup().getId() : null,
                    profile != null ? profile.getCity() : null,
                    profile != null ? profile.getCountry() : null,
                    profile != null ? profile.getFirstName() : null,
                    profile != null ? profile.getLastName() : null,
                    profile != null ? profile.getPhone() : null);

            return userView;

        } catch (Exception e) {
            log.error("Failed to edit user {} - Error: {}", user.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to edit user: " + e.getMessage(), e);
        }
    }

    public UserView deleteUserByEmail(String email) {
        return deleteUser(userService.getUserByEmail(email));
    }

    public UserView deleteUserById(int id) {
        return deleteUser(userService.getUserById(id));
    }

    @Transactional
    public UserView deleteUser(User user) {
        log.info("Deleting user with id {}", user.getId());
        checkResellerUserAccess(user);

        UserView userView = userViewMapper.toView(user);

        try {
            userService.deleteUser(user);
        } catch (Exception e) {
            log.error("Failed to delete user normally", e);
            // userService.forceDeleteUser(user);
        }

        User accessorUser = userService.getUser();
        log.info("Deleted user with username {} by user with username {}",
                user.getUsername(), accessorUser.getUsername());

        return userView;
    }

    public UserView getUser(int id) {
        User user = userService.getUserById(id);
        checkResellerUserAccess(user);
        return userService.getUserFullView(user);
    }

    public UserView getUserByEmail(String email) {
        User user = userService.getUserByEmail(email);

        // Dynamically resolve the active subscription
        UserSubscription currentSubscription = user.getCurrentSubscription();

        // Map the user to the UserView
        UserView userView = userViewMapper.toView(user);

        // Set the active subscription in UserView
        if (currentSubscription != null) {
            userView.setSubscription(userSubscriptionViewMapper.toView(currentSubscription));
            userView.setCurrentSubscription(currentSubscription);
        }

        return userView;
    }

    public UserView getUserByUsername(String username) {
        User user = userService.getUserByUsername(username);
        checkResellerUserAccess(user);
        return userService.getUserFullView(user);
    }

    public UserView getUserById(Integer id) {
        User user = userService.getUserById(id);
        checkResellerUserAccess(user);
        return userService.getUserFullView(user);
    }

    public Page<UserView> getUsers(int page, int size) {
        User accessorUser = userService.getUser();
        Reseller reseller = accessorUser.getReseller();
        Role accessorRole = accessorUser.getRole();
        Pageable pageable = PageRequest.of(page, size, Sort.by(DEFAULT_SORT).descending());

        Page<User> queryResult;
        if (accessorRole.getName() == RoleName.ADMIN) {
            queryResult = userRepository.findAll(pageable);
        } else {
            queryResult = userRepository.findAllByReseller(reseller, pageable);
        }

        return queryResult.map(userViewMapper::toView);
    }

    public Page<UserView> getExpiredUsers(int page, int size) {
        User accessorUser = userService.getUser();
        Reseller reseller = accessorUser.getReseller();
        Role accessorRole = accessorUser.getRole();
        Pageable pageable = PageRequest.of(page, size, Sort.by(DEFAULT_SORT));
        LocalDateTime dateTime = LocalDateTime.now();

        Page<User> queryResult;
        if (accessorRole.getName() == RoleName.ADMIN) {
            queryResult = userRepository.findAllExpiredUsers(dateTime, pageable);
        } else {
            queryResult = userRepository.findAllResellerExpiredUsers(reseller, dateTime, pageable);
        }

        return queryResult.map(userViewMapper::toView);
    }

    public void checkResellerUserAccess(User user) {
        User accessorUser = userService.getUser();
        Reseller reseller = accessorUser.getReseller();
        Role accessorRole = accessorUser.getRole();
        if (accessorRole.getName() != RoleName.ADMIN && user.getReseller().getId() != reseller
                .getId()) {
            throw new AccessDeniedException("Can't access user");
        }
    }

    /**
     * Update user's device count with proper charging for resellers.
     * Charges the reseller's credit for additional devices (pro-rata based on remaining subscription time).
     * No refund for device reductions.
     *
     * @param user            The user to update
     * @param newDeviceCount  The new total device count
     */
    @Transactional
    private void updateUserDeviceCountWithCharging(User user, int newDeviceCount) {
        UserSubscription subscription = userSubscriptionService.getCurrentSubscription(user);
        if (subscription == null) {
            throw new NotFoundException("User has no active subscription");
        }

        int currentDeviceCount = subscription.getMultiLoginCount();
        int deviceDifference = newDeviceCount - currentDeviceCount;

        if (deviceDifference == 0) {
            log.debug("Device count unchanged for user {}", user.getId());
            return;
        }

        User accessorUser = userService.getUser();
        Reseller reseller = accessorUser.getReseller();

        // If adding devices, calculate and charge
        if (deviceDifference > 0 && reseller != null) {
            // Check if reseller is OWNER (free) or needs to pay
            if (reseller.getLevel().getName() != ResellerLevelName.OWNER) {
                // Lock reseller to prevent race conditions
                Reseller lockedReseller = resellerRepository.findByIdWithLock(reseller.getId())
                        .orElseThrow(() -> new RuntimeException("Reseller not found"));

                // Calculate pro-rata price for the additional devices
                BigDecimal price = calculateDeviceAddonPrice(user, deviceDifference);

                // Check sufficient credit
                if (lockedReseller.getCredit().compareTo(price) < 0) {
                    throw new InsufficientFundsException(
                            String.format("Insufficient credit. Required: $%.2f, Available: $%.2f",
                                    price, lockedReseller.getCredit()));
                }

                // Deduct credit
                BigDecimal newBalance = lockedReseller.getCredit().subtract(price);
                lockedReseller.setCredit(newBalance);
                resellerRepository.save(lockedReseller);

                // Record transaction
                ResellerAddCredit creditTransaction = ResellerAddCredit.createDevicePurchase(
                        lockedReseller, price, newBalance, user.getId(), user.getEmail(), deviceDifference);
                resellerAddCreditRepository.save(creditTransaction);

                log.info("Charged reseller {} amount {} for {} devices added to user {}",
                        reseller.getId(), price, deviceDifference, user.getId());
            }
        }

        // Update the subscription
        userSubscriptionService.updateSubscriptionMultiLoginCount(user, newDeviceCount);

        log.info("Updated device count for user {} from {} to {} (diff: {})",
                user.getId(), currentDeviceCount, newDeviceCount, deviceDifference);
    }

    /**
     * Calculate the pro-rata price for device addons based on remaining subscription time.
     *
     * @param user        The user
     * @param deviceCount Number of devices to add
     * @return The calculated price
     */
    private BigDecimal calculateDeviceAddonPrice(User user, int deviceCount) {
        UserSubscription subscription = userSubscriptionService.getCurrentSubscription(user);
        Group group = subscription.getGroup();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = subscription.getExpiresAt();

        long remainingDays = java.time.temporal.ChronoUnit.DAYS.between(now, expiresAt);
        if (remainingDays <= 0) {
            remainingDays = 1;
        }

        int subscriptionDuration = subscription.getDuration();
        if (subscriptionDuration <= 0) {
            subscriptionDuration = 30;
        }

        BigDecimal groupPrice = group.getPrice();
        int baseDeviceCount = Math.max(group.getMultiLoginCount(), 1);

        // Daily rate per device = (groupPrice / duration) / baseDevices
        BigDecimal dailySubscriptionRate = groupPrice.divide(
                BigDecimal.valueOf(subscriptionDuration), 6, java.math.RoundingMode.HALF_UP);
        BigDecimal dailyRatePerDevice = dailySubscriptionRate.divide(
                BigDecimal.valueOf(baseDeviceCount), 6, java.math.RoundingMode.HALF_UP);

        // Apply reseller discount
        User accessor = userService.getUser();
        Reseller reseller = accessor.getReseller();
        BigDecimal discountMultiplier = BigDecimal.ONE;

        if (reseller != null && reseller.getLevel().getDiscountPercent() != null) {
            BigDecimal discountPercent = reseller.getLevel().getDiscountPercent();
            discountMultiplier = BigDecimal.ONE.subtract(
                    discountPercent.divide(new BigDecimal("100"), 4, java.math.RoundingMode.HALF_UP));
        }

        // Calculate final price
        BigDecimal price = dailyRatePerDevice
                .multiply(BigDecimal.valueOf(deviceCount))
                .multiply(BigDecimal.valueOf(remainingDays))
                .multiply(discountMultiplier)
                .setScale(2, java.math.RoundingMode.HALF_UP);

        // Minimum charge
        BigDecimal minimumCharge = new BigDecimal("0.50");
        if (price.compareTo(minimumCharge) < 0 && price.compareTo(BigDecimal.ZERO) > 0) {
            price = minimumCharge;
        }

        return price;
    }

    /**
     * Reset a user's password by admin/reseller.
     * Generates a new random password, sets it on the user's account,
     * and sends notifications to the user via all configured channels.
     * Returns the new password to the admin so they can communicate it if needed.
     *
     * @param userId The ID of the user whose password should be reset
     * @return AdminPasswordResetResult containing the new password and notification status
     */
    public AdminPasswordResetResult adminResetUserPassword(int userId) {
        log.info("Admin/Reseller resetting password for user ID: {}", userId);

        // Get the target user
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with ID: " + userId));

        // Check access permissions
        checkResellerUserAccess(targetUser);

        // Generate a secure random password (12 characters for better security)
        String newPassword = generateSecurePassword(12);

        // Set the new password
        passwordService.setPassword(targetUser, newPassword);
        userRepository.save(targetUser);

        log.info("Password reset successfully for user: {}", targetUser.getEmail());

        // Send notifications asynchronously via all configured channels
        boolean notificationSent = false;
        try {
            asyncNotificationHelper.sendAdminPasswordResetNotificationAsync(targetUser, newPassword);
            notificationSent = true;
            log.debug("Password reset notification sent to user: {}", targetUser.getEmail());
        } catch (Exception e) {
            log.error("Failed to send password reset notification to user {}: {}",
                    targetUser.getEmail(), e.getMessage());
        }

        return AdminPasswordResetResult.builder()
                .userId(targetUser.getId())
                .email(targetUser.getEmail())
                .username(targetUser.getUsername())
                .newPassword(newPassword)
                .emailNotificationSent(notificationSent)
                .otherNotificationsSent(notificationSent)
                .message("Password has been reset successfully. The user has been notified via configured channels.")
                .build();
    }

    /**
     * Reset a user's password by admin/reseller using email.
     * Generates a new random password, sets it on the user's account,
     * and sends notifications to the user via all configured channels.
     *
     * @param email The email of the user whose password should be reset
     * @return AdminPasswordResetResult containing the new password and notification status
     */
    public AdminPasswordResetResult adminResetUserPasswordByEmail(String email) {
        log.info("Admin/Reseller resetting password for user email: {}", email);

        // Get the target user
        User targetUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found with email: " + email));

        // Check access permissions
        checkResellerUserAccess(targetUser);

        // Generate a secure random password (12 characters for better security)
        String newPassword = generateSecurePassword(12);

        // Set the new password
        passwordService.setPassword(targetUser, newPassword);
        userRepository.save(targetUser);

        log.info("Password reset successfully for user: {}", targetUser.getEmail());

        // Send notifications asynchronously via all configured channels
        boolean notificationSent = false;
        try {
            asyncNotificationHelper.sendAdminPasswordResetNotificationAsync(targetUser, newPassword);
            notificationSent = true;
            log.debug("Password reset notification sent to user: {}", targetUser.getEmail());
        } catch (Exception e) {
            log.error("Failed to send password reset notification to user {}: {}",
                    targetUser.getEmail(), e.getMessage());
        }

        return AdminPasswordResetResult.builder()
                .userId(targetUser.getId())
                .email(targetUser.getEmail())
                .username(targetUser.getUsername())
                .newPassword(newPassword)
                .emailNotificationSent(notificationSent)
                .otherNotificationsSent(notificationSent)
                .message("Password has been reset successfully. The user has been notified via configured channels.")
                .build();
    }

    /**
     * Generate a secure random password with mixed character types.
     * Ensures the password contains at least one uppercase, one lowercase,
     * one digit, and one special character for strong security.
     *
     * @param length The desired password length (minimum 8)
     * @return A secure random password
     */
    private String generateSecurePassword(int length) {
        if (length < 8) {
            length = 8; // Minimum length for security
        }

        final String upperCase = "ABCDEFGHJKLMNPQRSTUVWXYZ"; // Excluding I, O for readability
        final String lowerCase = "abcdefghjkmnpqrstuvwxyz"; // Excluding i, l, o for readability
        final String digits = "23456789"; // Excluding 0, 1 for readability
        final String special = "!@#$%&*";

        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder password = new StringBuilder(length);

        // Ensure at least one of each required character type
        password.append(upperCase.charAt(random.nextInt(upperCase.length())));
        password.append(lowerCase.charAt(random.nextInt(lowerCase.length())));
        password.append(digits.charAt(random.nextInt(digits.length())));
        password.append(special.charAt(random.nextInt(special.length())));

        // Fill the remaining length with random characters from all types
        String allChars = upperCase + lowerCase + digits + special;
        for (int i = 4; i < length; i++) {
            password.append(allChars.charAt(random.nextInt(allChars.length())));
        }

        // Shuffle the password to avoid predictable positions
        char[] passwordArray = password.toString().toCharArray();
        for (int i = passwordArray.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char temp = passwordArray[i];
            passwordArray[i] = passwordArray[j];
            passwordArray[j] = temp;
        }

        return new String(passwordArray);
    }
}
