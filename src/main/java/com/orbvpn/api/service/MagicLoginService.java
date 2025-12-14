package com.orbvpn.api.service;

import com.orbvpn.api.domain.dto.AuthenticatedUser;
import com.orbvpn.api.domain.entity.MagicLoginCode;
import com.orbvpn.api.domain.entity.MagicLinkToken;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.exception.NotFoundException;
import com.orbvpn.api.repository.MagicLoginCodeRepository;
import com.orbvpn.api.repository.MagicLinkTokenRepository;
import com.orbvpn.api.repository.UserRepository;
import com.orbvpn.api.service.notification.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@Slf4j
@RequiredArgsConstructor
public class MagicLoginService {

    private final MagicLoginCodeRepository magicLoginCodeRepository;
    private final MagicLinkTokenRepository magicLinkTokenRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final NotificationService notificationService;
    private final AsyncNotificationHelper asyncNotificationHelper;

    private static final int CODE_EXPIRY_MINUTES = 10;
    private static final int LINK_EXPIRY_MINUTES = 15;
    private static final int RATE_LIMIT_MINUTES = 1;
    private static final int MAX_REQUESTS_PER_PERIOD = 3;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Request a magic login code to be sent to the user's email
     *
     * @param email the user's email address
     * @return true if the code was sent successfully
     */
    @Transactional
    public boolean requestMagicLogin(String email) {
        log.info("Magic login requested for email: {}", email);

        // Find the user by email
        User user = userRepository.findByEmailAndActiveTrue(email)
                .orElseThrow(() -> {
                    log.warn("Magic login requested for non-existent or inactive email: {}", email);
                    // Return generic message to prevent email enumeration
                    return new NotFoundException("User not found");
                });

        // Rate limiting: check if user has requested too many codes recently
        LocalDateTime rateLimitSince = LocalDateTime.now().minusMinutes(RATE_LIMIT_MINUTES);
        long recentRequests = magicLoginCodeRepository.countRecentCodesForUser(user, rateLimitSince);
        if (recentRequests >= MAX_REQUESTS_PER_PERIOD) {
            log.warn("Rate limit exceeded for magic login - email: {}, requests: {}", email, recentRequests);
            throw new BadCredentialsException("Too many login requests. Please wait a moment before trying again.");
        }

        // Invalidate any existing codes for this user
        magicLoginCodeRepository.invalidateAllCodesForUser(user);

        // Generate a new 6-digit code
        String code = generateCode();

        // Create and save the magic login code
        MagicLoginCode magicLoginCode = new MagicLoginCode();
        magicLoginCode.setCode(code);
        magicLoginCode.setUser(user);
        magicLoginCode.setExpiresAt(LocalDateTime.now().plusMinutes(CODE_EXPIRY_MINUTES));
        magicLoginCode.setUsed(false);

        magicLoginCodeRepository.save(magicLoginCode);

        // Send the code via email asynchronously
        asyncNotificationHelper.sendMagicLoginCodeAsync(user, code);

        log.info("Magic login code sent successfully for email: {}", email);
        return true;
    }

    /**
     * Verify a magic login code and authenticate the user
     *
     * @param email the user's email address
     * @param code  the 6-digit code received by email
     * @return AuthenticatedUser containing tokens and user info
     */
    @Transactional
    public AuthenticatedUser verifyMagicLogin(String email, String code) {
        log.info("Verifying magic login for email: {}", email);

        // Find the user by email
        User user = userRepository.findByEmailAndActiveTrue(email)
                .orElseThrow(() -> {
                    log.warn("Magic login verification for non-existent or inactive email: {}", email);
                    return new BadCredentialsException("Invalid email or code");
                });

        // Find a valid magic login code for this user
        LocalDateTime now = LocalDateTime.now();
        MagicLoginCode magicLoginCode = magicLoginCodeRepository
                .findValidCodeByUserAndCode(user, code, now)
                .orElseThrow(() -> {
                    log.warn("Invalid or expired magic login code for email: {}", email);
                    return new BadCredentialsException("Invalid or expired code. Please request a new one.");
                });

        // Mark the code as used
        magicLoginCode.setUsed(true);
        magicLoginCodeRepository.save(magicLoginCode);

        log.info("Magic login successful for email: {}", email);

        // Return authenticated user with tokens
        return userService.loginInfo(user);
    }

    /**
     * Generate a secure random 6-digit code
     */
    private String generateCode() {
        int code = secureRandom.nextInt(900000) + 100000; // Generates 100000-999999
        return String.valueOf(code);
    }

    /**
     * Cleanup expired magic login codes (can be called by a scheduled job)
     */
    @Transactional
    public int cleanupExpiredCodes() {
        int deleted = magicLoginCodeRepository.deleteExpiredCodes(LocalDateTime.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired magic login codes", deleted);
        }
        return deleted;
    }

    // ==================== MAGIC LINK METHODS ====================

    /**
     * Request a magic link to be sent to the user's email.
     * Unlike magic login codes (6-digit), magic links are URL-based tokens that users can click directly.
     *
     * @param email the user's email address
     * @return true if the link was sent successfully
     */
    @Transactional
    public boolean requestMagicLink(String email) {
        log.info("Magic link requested for email: {}", email);

        // Find the user by email
        User user = userRepository.findByEmailAndActiveTrue(email)
                .orElseThrow(() -> {
                    log.warn("Magic link requested for non-existent or inactive email: {}", email);
                    // Return generic message to prevent email enumeration
                    return new NotFoundException("User not found");
                });

        // Rate limiting: check if user has requested too many tokens recently
        LocalDateTime rateLimitSince = LocalDateTime.now().minusMinutes(RATE_LIMIT_MINUTES);
        long recentRequests = magicLinkTokenRepository.countRecentTokensForUser(user, rateLimitSince);
        if (recentRequests >= MAX_REQUESTS_PER_PERIOD) {
            log.warn("Rate limit exceeded for magic link - email: {}, requests: {}", email, recentRequests);
            throw new BadCredentialsException("Too many login requests. Please wait a moment before trying again.");
        }

        // Invalidate any existing tokens for this user
        magicLinkTokenRepository.invalidateAllTokensForUser(user);

        // Generate a new secure token
        String token = generateToken();

        // Create and save the magic link token
        MagicLinkToken magicLinkToken = new MagicLinkToken();
        magicLinkToken.setToken(token);
        magicLinkToken.setUser(user);
        magicLinkToken.setExpiresAt(LocalDateTime.now().plusMinutes(LINK_EXPIRY_MINUTES));
        magicLinkToken.setUsed(false);

        magicLinkTokenRepository.save(magicLinkToken);

        // Send the magic link via email asynchronously
        asyncNotificationHelper.sendMagicLinkAsync(user, token);

        log.info("Magic link sent successfully for email: {}", email);
        return true;
    }

    /**
     * Verify a magic link token and authenticate the user
     *
     * @param token the token from the magic link URL
     * @return AuthenticatedUser containing tokens and user info
     */
    @Transactional
    public AuthenticatedUser verifyMagicLink(String token) {
        log.info("Verifying magic link token");

        // Find a valid magic link token
        LocalDateTime now = LocalDateTime.now();
        MagicLinkToken magicLinkToken = magicLinkTokenRepository
                .findValidToken(token, now)
                .orElseThrow(() -> {
                    log.warn("Invalid or expired magic link token");
                    return new BadCredentialsException("Invalid or expired link. Please request a new one.");
                });

        // Mark the token as used
        magicLinkToken.setUsed(true);
        magicLinkTokenRepository.save(magicLinkToken);

        User user = magicLinkToken.getUser();
        log.info("Magic link login successful for email: {}", user.getEmail());

        // Return authenticated user with tokens
        return userService.loginInfo(user);
    }

    /**
     * Generate a secure random URL-safe token (32 bytes = 256 bits)
     */
    private String generateToken() {
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    /**
     * Cleanup expired magic link tokens (can be called by a scheduled job)
     */
    @Transactional
    public int cleanupExpiredTokens() {
        int deleted = magicLinkTokenRepository.deleteExpiredTokens(LocalDateTime.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired magic link tokens", deleted);
        }
        return deleted;
    }
}
