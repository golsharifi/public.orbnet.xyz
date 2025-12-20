package com.orbvpn.api.service;

import com.orbvpn.api.domain.entity.AdViewingSession;
import com.orbvpn.api.domain.entity.AdViewingSession.AdSessionStatus;
import com.orbvpn.api.domain.entity.TokenBalance;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.exception.AdLimitExceededException;
import com.orbvpn.api.exception.BadRequestException;
import com.orbvpn.api.exception.NotFoundException;
import com.orbvpn.api.repository.AdViewingSessionRepository;
import com.orbvpn.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Set;

/**
 * Service for verifying ad viewing before granting tokens.
 * Implements a secure session-based approach:
 * 1. Client requests ad session (server generates session ID + signature)
 * 2. Client watches ad
 * 3. Client submits completion with session ID, signature, and duration
 * 4. Server verifies and grants tokens
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AdVerificationService {

    private final AdViewingSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final TokenServiceInterface tokenService;

    @Value("${ad.verification.secret:defaultAdVerificationSecretKey123!}")
    private String verificationSecret;

    @Value("${ad.session.expiry.minutes:5}")
    private int sessionExpiryMinutes;

    @Value("${ad.min.duration.seconds:15}")
    private int minAdDurationSeconds;

    @Value("${ad.max.pending.sessions:3}")
    private int maxPendingSessions;

    @Value("${ad.max.sessions.per.ip.per.hour:20}")
    private int maxSessionsPerIpPerHour;

    @Value("${ad.max.sessions.per.device.per.hour:15}")
    private int maxSessionsPerDevicePerHour;

    private static final Set<String> ALLOWED_AD_VENDORS = Set.of(
        "GOOGLE", "UNITY", "APPLOVIN", "FACEBOOK", "ADMOB", "IRONSOURCE", "DEFAULT"
    );

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Request a new ad viewing session.
     * Returns session ID and signature for the client.
     */
    @Transactional
    public AdSessionResponse requestAdSession(Integer userId, String adVendor, String region,
                                              String deviceId, String ipAddress) {
        log.info("Ad session requested: userId={}, vendor={}, region={}", userId, adVendor, region);

        // Validate ad vendor
        String normalizedVendor = adVendor != null ? adVendor.toUpperCase() : "DEFAULT";
        if (!ALLOWED_AD_VENDORS.contains(normalizedVendor)) {
            log.warn("Invalid ad vendor requested: {}", adVendor);
            throw new BadRequestException("Invalid ad vendor: " + adVendor);
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found: " + userId));

        // Check for too many pending sessions (prevent abuse)
        long pendingCount = sessionRepository.countPendingSessionsForUser(userId, LocalDateTime.now());
        if (pendingCount >= maxPendingSessions) {
            log.warn("Too many pending sessions for user: {}", userId);
            throw new AdLimitExceededException(
                AdLimitExceededException.LimitType.UNKNOWN,
                (int) pendingCount,
                maxPendingSessions
            );
        }

        // IP rate limiting
        if (ipAddress != null) {
            long ipSessionCount = sessionRepository.countRecentSessionsFromIp(
                ipAddress, LocalDateTime.now().minusHours(1));
            if (ipSessionCount >= maxSessionsPerIpPerHour) {
                log.warn("IP rate limit exceeded: {}", ipAddress);
                throw new AdLimitExceededException(
                    AdLimitExceededException.LimitType.HOURLY,
                    (int) ipSessionCount,
                    maxSessionsPerIpPerHour
                );
            }
        }

        // Device rate limiting
        if (deviceId != null) {
            long deviceSessionCount = sessionRepository.countRecentSessionsFromDevice(
                deviceId, LocalDateTime.now().minusHours(1));
            if (deviceSessionCount >= maxSessionsPerDevicePerHour) {
                log.warn("Device rate limit exceeded: {}", deviceId);
                throw new AdLimitExceededException(
                    AdLimitExceededException.LimitType.HOURLY,
                    (int) deviceSessionCount,
                    maxSessionsPerDevicePerHour
                );
            }
        }

        // Generate unique session ID
        String sessionId = generateSessionId();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(sessionExpiryMinutes);

        // Generate verification signature
        String signature = generateSignature(sessionId, userId, normalizedVendor, expiresAt);

        // Create session record
        AdViewingSession session = AdViewingSession.builder()
            .sessionId(sessionId)
            .user(user)
            .adVendor(normalizedVendor)
            .region(region != null ? region.toUpperCase() : "DEFAULT")
            .deviceId(deviceId)
            .ipAddress(ipAddress)
            .expiresAt(expiresAt)
            .status(AdSessionStatus.PENDING)
            .minDurationSeconds(minAdDurationSeconds)
            .tokensGranted(false)
            .verificationSignature(signature)
            .build();

        sessionRepository.save(session);

        log.info("Ad session created: sessionId={}, userId={}, expiresAt={}", sessionId, userId, expiresAt);

        return new AdSessionResponse(sessionId, signature, expiresAt, minAdDurationSeconds);
    }

    /**
     * Complete an ad viewing session and earn tokens.
     */
    @Transactional
    public TokenBalance completeAdSession(Integer userId, String sessionId, String signature,
                                          int reportedDurationSeconds, String ipAddress) {
        log.info("Ad session completion: userId={}, sessionId={}, duration={}s",
                 userId, sessionId, reportedDurationSeconds);

        // Find the session
        AdViewingSession session = sessionRepository.findBySessionId(sessionId)
            .orElseThrow(() -> {
                log.warn("Session not found: {}", sessionId);
                return new BadRequestException("Invalid session ID");
            });

        // Verify session belongs to user
        if (!userId.equals(session.getUser().getId())) {
            log.warn("Session {} does not belong to user {}", sessionId, userId);
            session.setStatus(AdSessionStatus.REJECTED);
            session.setRejectionReason("User mismatch");
            sessionRepository.save(session);
            throw new BadRequestException("Session does not belong to this user");
        }

        // Check session status
        if (session.getStatus() != AdSessionStatus.PENDING) {
            log.warn("Session {} is not pending, status={}", sessionId, session.getStatus());
            throw new BadRequestException("Session is not pending: " + session.getStatus());
        }

        // Check expiration
        if (LocalDateTime.now().isAfter(session.getExpiresAt())) {
            log.warn("Session {} has expired", sessionId);
            session.setStatus(AdSessionStatus.EXPIRED);
            sessionRepository.save(session);
            throw new BadRequestException("Session has expired");
        }

        // Verify signature
        String expectedSignature = generateSignature(
            sessionId, userId, session.getAdVendor(), session.getExpiresAt());
        if (!expectedSignature.equals(signature)) {
            log.warn("Invalid signature for session {}", sessionId);
            session.setStatus(AdSessionStatus.REJECTED);
            session.setRejectionReason("Invalid signature");
            sessionRepository.save(session);
            throw new BadRequestException("Invalid session signature");
        }

        // Verify minimum duration
        if (reportedDurationSeconds < session.getMinDurationSeconds()) {
            log.warn("Duration too short for session {}: {}s < {}s",
                     sessionId, reportedDurationSeconds, session.getMinDurationSeconds());
            session.setStatus(AdSessionStatus.REJECTED);
            session.setRejectionReason("Duration too short: " + reportedDurationSeconds + "s");
            sessionRepository.save(session);
            throw new BadRequestException(
                "Ad must be watched for at least " + session.getMinDurationSeconds() + " seconds");
        }

        // All checks passed - complete session and grant tokens
        session.setStatus(AdSessionStatus.COMPLETED);
        session.setCompletedAt(LocalDateTime.now());
        session.setReportedDurationSeconds(reportedDurationSeconds);
        session.setTokensGranted(true);
        sessionRepository.save(session);

        // Grant tokens using the token service
        TokenBalance balance = tokenService.earnTokens(userId, session.getAdVendor(), session.getRegion());

        log.info("Ad session completed successfully: sessionId={}, userId={}", sessionId, userId);

        return balance;
    }

    /**
     * Generate a unique session ID.
     */
    private String generateSessionId() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Generate HMAC signature for session verification.
     */
    private String generateSignature(String sessionId, Integer userId,
                                     String adVendor, LocalDateTime expiresAt) {
        try {
            String data = sessionId + "|" + userId + "|" + adVendor + "|" + expiresAt.toString();
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                verificationSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to generate signature", e);
        }
    }

    /**
     * Cleanup expired sessions periodically.
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    @Transactional
    public void cleanupExpiredSessions() {
        int expired = sessionRepository.markExpiredSessions(LocalDateTime.now());
        if (expired > 0) {
            log.info("Marked {} sessions as expired", expired);
        }

        // Delete sessions older than 7 days
        int deleted = sessionRepository.deleteOldSessions(LocalDateTime.now().minusDays(7));
        if (deleted > 0) {
            log.info("Deleted {} old sessions", deleted);
        }
    }

    /**
     * Response object for ad session request.
     */
    public record AdSessionResponse(
        String sessionId,
        String signature,
        LocalDateTime expiresAt,
        int minDurationSeconds
    ) {}
}
