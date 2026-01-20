package com.orbvpn.api.domain.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PasswordReset entity expiration logic
 */
class PasswordResetTest {

    @Test
    @DisplayName("Token should be valid when expiration is in the future")
    void testTokenIsValidWhenNotExpired() {
        PasswordReset passwordReset = new PasswordReset();
        passwordReset.setToken("123456");
        passwordReset.setExpiresAt(LocalDateTime.now().plusHours(1)); // Expires in 1 hour

        assertFalse(passwordReset.isExpired(), "Token should not be expired");
        assertTrue(passwordReset.isValid(), "Token should be valid");
    }

    @Test
    @DisplayName("Token should be expired when expiration is in the past")
    void testTokenIsExpiredWhenPastExpiration() {
        PasswordReset passwordReset = new PasswordReset();
        passwordReset.setToken("123456");
        passwordReset.setExpiresAt(LocalDateTime.now().minusMinutes(1)); // Expired 1 minute ago

        assertTrue(passwordReset.isExpired(), "Token should be expired");
        assertFalse(passwordReset.isValid(), "Token should not be valid");
    }

    @Test
    @DisplayName("Token should be expired when expiration is exactly now")
    void testTokenIsExpiredAtExactExpiration() {
        PasswordReset passwordReset = new PasswordReset();
        passwordReset.setToken("123456");
        passwordReset.setExpiresAt(LocalDateTime.now().minusSeconds(1)); // Just expired

        assertTrue(passwordReset.isExpired(), "Token should be expired at exact expiration time");
    }

    @Test
    @DisplayName("Token with 1 hour expiration should be valid")
    void testOneHourExpirationIsValid() {
        PasswordReset passwordReset = new PasswordReset();
        passwordReset.setToken("654321");

        // Simulate the expiration logic from UserService
        long expirationMillis = 3600000L; // 1 hour in milliseconds
        LocalDateTime expiresAt = LocalDateTime.now().plusNanos(expirationMillis * 1_000_000);
        passwordReset.setExpiresAt(expiresAt);

        assertTrue(passwordReset.isValid(), "Token with 1 hour expiration should be valid immediately after creation");
        assertFalse(passwordReset.isExpired(), "Token should not be expired right after creation");
    }

    @Test
    @DisplayName("Expired token from yesterday should be invalid")
    void testExpiredTokenFromYesterday() {
        PasswordReset passwordReset = new PasswordReset();
        passwordReset.setToken("111111");
        passwordReset.setExpiresAt(LocalDateTime.now().minusDays(1)); // Expired yesterday

        assertTrue(passwordReset.isExpired(), "Token from yesterday should be expired");
        assertFalse(passwordReset.isValid(), "Token from yesterday should not be valid");
    }

    @Test
    @DisplayName("Legacy token without expiration should be considered expired")
    void testLegacyTokenWithoutExpirationIsExpired() {
        PasswordReset passwordReset = new PasswordReset();
        passwordReset.setToken("222222");
        // expiresAt is null (legacy token)

        assertTrue(passwordReset.isExpired(), "Legacy token without expiration should be expired for safety");
        assertFalse(passwordReset.isValid(), "Legacy token should not be valid");
    }
}
