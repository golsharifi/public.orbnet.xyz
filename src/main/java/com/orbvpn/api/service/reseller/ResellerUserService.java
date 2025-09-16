package com.orbvpn.api.service.reseller;

import com.orbvpn.api.config.LocaleConfig;
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
import com.orbvpn.api.repository.ResellerRepository;
import com.orbvpn.api.repository.UserProfileRepository;
import com.orbvpn.api.repository.UserRepository;
import com.orbvpn.api.service.webhook.WebhookEventCreator;
import com.orbvpn.api.service.webhook.WebhookService;
import com.orbvpn.api.service.subscription.UserSubscriptionService;
import com.orbvpn.api.service.UserService;
import com.orbvpn.api.service.GroupService;
import com.orbvpn.api.service.RoleService;
import com.orbvpn.api.service.PasswordService;
import com.orbvpn.api.service.notification.NotificationService;
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
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final WebhookService webhookService;
    private final WebhookEventCreator webhookEventCreator;
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

        try {
            notificationService.welcomingNewUsersCreatedByAdmin(user, sub, resellerUserCreate.getPassword());
            log.info("Successfully sent welcome notification to user {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to send welcome notification to user {}: {}", user.getId(), e.getMessage(), e);
            log.warn("User {} was created successfully, but welcome notification failed", user.getId());
            // Don't throw the exception - user creation should still succeed
        }

        log.info("Created user");
        // Add webhook event before returning
        webhookService.processWebhook("USER_CREATED",
                webhookEventCreator.createUserPayload(user, "USER_CREATED"));
        return userView;
    }

    @Transactional
    public void createResellerUserSubscription(User user, Group group) {
        log.info("Creating reseller subscription for user {} with group {}", user.getId(), group.getId());

        try {
            Reseller reseller = user.getReseller();
            BigDecimal credit = reseller.getCredit();
            BigDecimal price = calculatePrice(reseller, group);

            if (credit.compareTo(price) < 0) {
                throw new InsufficientFundsException();
            }

            // Update reseller credit
            reseller.setCredit(credit.subtract(price));
            resellerRepository.save(reseller);

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
        if (level.getName() == ResellerLevelName.OWNER) {
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

            // MultiLogin update
            Integer multiLoginCount = resellerUserEdit.getMultiLoginCount();
            if (multiLoginCount != null) {
                log.debug("Updating multiLoginCount to {} for user {}", multiLoginCount, user.getId());
                userSubscriptionService.updateSubscriptionMultiLoginCount(user, multiLoginCount);
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
}
