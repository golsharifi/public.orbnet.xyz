package com.orbvpn.api.service;

import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.repository.*;
import com.orbvpn.api.domain.enums.RoleName;
import com.orbvpn.api.service.notification.NotificationService;
import com.orbvpn.api.config.LocaleConfig;
import com.orbvpn.api.domain.dto.AuthenticatedUser;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.apache.commons.codec.digest.DigestUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class EmailVerificationService {

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;
    @Autowired
    private PasswordResetRepository passwordResetRepository;

    @Autowired
    private UnverifiedUserRepository unverifiedUserRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleService roleService;

    @Autowired
    private ResellerRepository resellerRepository;

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private AsyncNotificationHelper asyncNotificationHelper;

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordService passwordService; // Inject PasswordService to handle rad_access password

    @Autowired
    private UserUuidService userUuidService;

    @Transactional
    public AuthenticatedUser verifyEmailWithCode(String email, String verificationCode) {
        Optional<UnverifiedUser> optionalUser = unverifiedUserRepository.findByEmail(email);
        if (!optionalUser.isPresent()) {
            throw new RuntimeException("Email not found.");
        }

        UnverifiedUser unverifiedUser = optionalUser.get();
        Optional<VerificationToken> optionalToken = verificationTokenRepository.findByUser(unverifiedUser);

        if (!optionalToken.isPresent()) {
            throw new RuntimeException("Verification code not found.");
        }

        VerificationToken verificationToken = optionalToken.get();
        if (verificationToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Verification code has expired.");
        }

        if (!verificationToken.getVerificationCode().equals(verificationCode)) {
            throw new RuntimeException("Incorrect verification code.");
        }

        Optional<User> existingUserByEmail = userRepository.findByEmail(unverifiedUser.getEmail());
        Optional<User> existingUserByUsername = userRepository.findByUsername(unverifiedUser.getEmail());

        if (existingUserByEmail.isPresent() || existingUserByUsername.isPresent()) {
            throw new RuntimeException("User with the given email or username already exists.");
        }

        User user = new User();
        user.setEmail(unverifiedUser.getEmail());
        user.setUsername(unverifiedUser.getEmail());
        user.setPassword(unverifiedUser.getPassword());
        user.setAesKey(unverifiedUser.getAesKey()); // Copy AES key
        user.setAesIv(unverifiedUser.getAesIv()); // Copy AES IV

        // Set rad_access password
        user.setRadAccess(DigestUtils.sha1Hex(passwordService.getPassword(unverifiedUser)));

        Role defaultRole = roleService.getByName(RoleName.USER);
        user.setRole(defaultRole);

        Reseller defaultReseller = resellerRepository.getReferenceById(1);
        user.setReseller(defaultReseller);

        // Create UserProfile for the new user
        UserProfile profile = new UserProfile();
        profile.setUser(user);
        // Set language from unverified user if available
        if (unverifiedUser.getLanguage() != null) {
            profile.setLanguage(unverifiedUser.getLanguage());
        }
        user.setProfile(profile);

        try {
            userRepository.save(user);
            // Generate and set UUID
            String uuid = userUuidService.getOrCreateUuid(user.getId());
            user.setUuid(uuid);
            user = userRepository.save(user);

        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("Error during email verification: " + e.getMessage());
        }

        if (verificationTokenRepository.existsById(verificationToken.getId())) {
            verificationTokenRepository.delete(verificationToken);
        } else {
            throw new RuntimeException("Code doesn't exist in the database.");
        }

        unverifiedUserRepository.delete(unverifiedUser);

        // Determine the user's locale
        Locale userLocale = unverifiedUser.getLanguage() != null
                ? unverifiedUser.getLanguage()
                : LocaleConfig.DEFAULT_LOCALE;

        // Send a success email to the user asynchronously to avoid blocking
        asyncNotificationHelper.sendSuccessVerificationEmailAsync(user.getEmail(), userLocale);

        return userService.loginInfo(user);
    }

    @Transactional
    public Boolean verifyResetPasswordCode(String email, String verificationCode) {
        Optional<User> optionalUser = userRepository.findByEmail(email);
        if (!optionalUser.isPresent()) {
            throw new RuntimeException("Email not found.");
        }

        User user = optionalUser.get();
        Optional<PasswordReset> optionalPasswordReset = passwordResetRepository.findByUser(user);

        if (!optionalPasswordReset.isPresent()) {
            throw new RuntimeException("Verification code not found.");
        }

        PasswordReset passwordReset = optionalPasswordReset.get();

        // Check if the reset token has expired
        if (passwordReset.isExpired()) {
            // Clean up expired token
            passwordResetRepository.delete(passwordReset);
            throw new RuntimeException("Verification code has expired. Please request a new password reset.");
        }

        if (!passwordReset.getToken().equals(verificationCode)) {
            throw new RuntimeException("Incorrect verification code.");
        }

        return true;
    }

    public void resendVerificationEmail(String email) {
        RateLimit rateLimit = rateLimitService.getOrCreateRateLimit(email);

        if (!rateLimitService.canProceedWithRequest(rateLimit)) {
            throw new RuntimeException(
                    "You've requested the verification email too many times. Please wait for a while before requesting again.");
        }

        Optional<UnverifiedUser> optionalUser = unverifiedUserRepository.findByEmail(email);
        if (!optionalUser.isPresent()) {
            throw new RuntimeException("Email not found.");
        }

        UnverifiedUser unverifiedUser = optionalUser.get();

        // Generate a new 6-digit verification code using SecureRandom for security
        SecureRandom secureRandom = new SecureRandom();
        String verificationCode = String.format("%06d", secureRandom.nextInt(999999));
        VerificationToken newVerificationToken = new VerificationToken();
        newVerificationToken.setUser(unverifiedUser);
        newVerificationToken.setVerificationCode(verificationCode);
        newVerificationToken.setExpiryDate(LocalDateTime.now().plusHours(3)); // Token valid for 3 hours

        // Save the new token and delete the old one if exists
        verificationTokenRepository.findByUser(unverifiedUser).ifPresent(verificationTokenRepository::delete);
        verificationTokenRepository.save(newVerificationToken);

        // Determine the user's locale
        Locale userLocale = unverifiedUser.getLanguage() != null
                ? unverifiedUser.getLanguage()
                : LocaleConfig.DEFAULT_LOCALE;
        // Send the verification email asynchronously to avoid blocking
        asyncNotificationHelper.sendVerificationEmailAsync(email, verificationCode, userLocale);
    }
}
