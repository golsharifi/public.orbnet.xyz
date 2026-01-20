package com.orbvpn.api.service;

import com.orbvpn.api.domain.entity.UnverifiedUser;
import com.orbvpn.api.domain.entity.VerificationToken;
import com.orbvpn.api.repository.UnverifiedUserRepository;
import com.orbvpn.api.repository.VerificationTokenRepository;
import com.orbvpn.api.repository.UserRepository;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.config.LocaleConfig;
import com.orbvpn.api.domain.dto.SignupResponse;
import com.orbvpn.api.service.notification.LocaleResolverService;

import org.springframework.context.MessageSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.util.Random;
import java.util.Optional;
import jakarta.mail.MessagingException;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
@Slf4j
public class UserRegistrationService {

    @Autowired
    private UnverifiedUserRepository unverifiedUserRepository;

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AsyncNotificationHelper asyncNotificationHelper;

    @Autowired
    private LocaleResolverService localeResolverService;

    @Autowired
    private MessageSource messageSource;

    @Transactional
    public SignupResponse register(UnverifiedUser user) throws MessagingException {
        String email = user.getEmail();

        // Check if email exists in User table
        Optional<User> existingUser = userRepository.findByEmail(email);
        if (existingUser.isPresent()) {
            return new SignupResponse("User with the given email already exists.", false);
        }

        // Check if email exists in UnverifiedUser table
        Optional<UnverifiedUser> existingUnverifiedUser = unverifiedUserRepository.findByEmail(email);
        if (existingUnverifiedUser.isPresent()) {
            return new SignupResponse(
                    "Email already registered but not verified. Please check your email or request a new verification link.",
                    false);
        }

        // Save the user in the UnverifiedUser table
        unverifiedUserRepository.save(user);

        // Resolve locale with proper fallback
        Locale userLocale;
        if (user.getLanguage() != null) {
            String languageCode = user.getLanguage().getLanguage();
            Locale requestedLocale = Locale.of(languageCode);
            if (localeResolverService.isSupported(requestedLocale)) {
                userLocale = localeResolverService.resolveLocale(languageCode);
            } else {
                userLocale = LocaleConfig.DEFAULT_LOCALE;
                log.warn("Unsupported language requested: {}. Falling back to default.", languageCode);
            }
        } else {
            userLocale = LocaleConfig.DEFAULT_LOCALE;
        }

        // Generate a verification token
        String verificationCode = String.format("%06d", new Random().nextInt(999999));

        VerificationToken verificationToken = new VerificationToken();
        verificationToken.setUser(user);
        verificationToken.setVerificationCode(verificationCode);
        verificationToken.setExpiryDate(LocalDateTime.now().plusHours(3)); // Token valid for 3 hours

        verificationTokenRepository.save(verificationToken);

        // Use async helper to send the verification email without blocking
        asyncNotificationHelper.sendVerificationEmailAsync(user.getEmail(), verificationCode, userLocale);

        // Return localized response message
        String message = messageSource.getMessage(
                "registration.verification.required",
                null,
                "Please verify your email to complete the registration.",
                userLocale);

        return new SignupResponse(message, true);
    }
}