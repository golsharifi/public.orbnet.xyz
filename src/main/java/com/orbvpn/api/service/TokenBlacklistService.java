package com.orbvpn.api.service;

import com.orbvpn.api.domain.entity.RevokedToken;
import com.orbvpn.api.repository.RevokedTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing JWT token blacklist/revocation.
 * Uses both database persistence and in-memory cache for fast lookups.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final RevokedTokenRepository revokedTokenRepository;

    /**
     * In-memory cache of revoked token JTIs for fast lookup.
     * Tokens are automatically removed when they expire.
     */
    private final Set<String> revokedTokenCache = ConcurrentHashMap.newKeySet();

    /**
     * Check if a token is revoked/blacklisted.
     * First checks in-memory cache, then database.
     *
     * @param token The JWT token to check
     * @return true if the token is revoked
     */
    public boolean isTokenRevoked(String token) {
        String jti = generateJti(token);

        // Fast path: check in-memory cache
        if (revokedTokenCache.contains(jti)) {
            return true;
        }

        // Slow path: check database (and update cache if found)
        boolean exists = revokedTokenRepository.existsByJti(jti);
        if (exists) {
            revokedTokenCache.add(jti);
        }
        return exists;
    }

    /**
     * Revoke a token (add to blacklist).
     *
     * @param token The JWT token to revoke
     * @param userId User ID who owned the token
     * @param username Username for audit
     * @param tokenType "access" or "refresh"
     * @param expiresAt When the token would naturally expire
     * @param reason Reason for revocation
     * @param ip IP address from which revocation was initiated
     */
    @Transactional
    public void revokeToken(String token, Integer userId, String username,
                           String tokenType, Date expiresAt, String reason, String ip) {
        String jti = generateJti(token);

        // Check if already revoked
        if (revokedTokenRepository.existsByJti(jti)) {
            log.debug("Token already revoked: jti={}", jti);
            return;
        }

        LocalDateTime expiresAtLocal = expiresAt.toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime();

        RevokedToken revokedToken = RevokedToken.builder()
            .jti(jti)
            .userId(userId)
            .username(username)
            .tokenType(tokenType)
            .expiresAt(expiresAtLocal)
            .reason(reason)
            .revokedFromIp(ip)
            .build();

        revokedTokenRepository.save(revokedToken);
        revokedTokenCache.add(jti);

        log.info("Token revoked: user={}, type={}, reason={}", username, tokenType, reason);
    }

    /**
     * Revoke all tokens for a user (e.g., on password change, account deletion).
     *
     * @param userId User ID
     * @param username Username
     * @param reason Reason for revocation
     */
    @Transactional
    public void revokeAllUserTokens(Integer userId, String username, String reason) {
        log.info("Revoking all tokens for user: {} ({}), reason: {}", username, userId, reason);
        // Note: We can't revoke tokens we don't know about.
        // This method is for future reference when we track active tokens.
        // For now, the short token expiration (15 min) mitigates this.
    }

    /**
     * Generate a unique identifier (JTI) for a token.
     * Uses SHA-256 hash of the token.
     */
    private String generateJti(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Cleanup expired tokens from the database.
     * Runs every hour.
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    @Transactional
    public void cleanupExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();
        int deleted = revokedTokenRepository.deleteExpiredTokens(now);
        if (deleted > 0) {
            log.info("Cleaned up {} expired revoked tokens", deleted);
        }

        // Also clean up in-memory cache by reloading from database
        // This is a simple approach; a more sophisticated one would track expiry times
        revokedTokenCache.clear();
    }

    /**
     * Get statistics about revoked tokens (for admin dashboard).
     */
    public long getRevokedTokenCount() {
        return revokedTokenRepository.count();
    }

    /**
     * Get revoked token count for a specific user.
     */
    public long getRevokedTokenCountForUser(Integer userId) {
        return revokedTokenRepository.countByUserId(userId);
    }
}
