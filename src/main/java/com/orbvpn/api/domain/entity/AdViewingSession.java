package com.orbvpn.api.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Tracks ad viewing sessions for verification.
 * Before users can earn tokens, they must:
 * 1. Request an ad session (server creates this record with a session ID)
 * 2. Watch the ad (client-side)
 * 3. Complete the session with a valid signature
 */
@Entity
@Table(name = "ad_viewing_session", indexes = {
    @Index(name = "idx_ad_session_id", columnList = "session_id"),
    @Index(name = "idx_ad_session_user", columnList = "user_id"),
    @Index(name = "idx_ad_session_created", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdViewingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique session identifier (UUID).
     */
    @Column(name = "session_id", nullable = false, unique = true, length = 64)
    private String sessionId;

    /**
     * User who requested the ad session.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Ad vendor (e.g., "GOOGLE", "UNITY", "APPLOVIN").
     */
    @Column(name = "ad_vendor", nullable = false, length = 50)
    private String adVendor;

    /**
     * Region/country for the ad request.
     */
    @Column(name = "region", length = 10)
    private String region;

    /**
     * Device ID that requested the ad.
     */
    @Column(name = "device_id", length = 100)
    private String deviceId;

    /**
     * IP address from which the ad was requested.
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /**
     * Session creation time.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Session expiration time (typically 5 minutes after creation).
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * When the session was completed (ad watched).
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /**
     * Session status: PENDING, COMPLETED, EXPIRED, REJECTED.
     */
    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AdSessionStatus status;

    /**
     * Minimum ad watch duration in seconds required for completion.
     */
    @Column(name = "min_duration_seconds")
    private Integer minDurationSeconds;

    /**
     * Actual ad watch duration reported by client.
     */
    @Column(name = "reported_duration_seconds")
    private Integer reportedDurationSeconds;

    /**
     * HMAC signature for verification.
     * Client must send back this signature to prove the session is valid.
     */
    @Column(name = "verification_signature", length = 128)
    private String verificationSignature;

    /**
     * Whether tokens were granted for this session.
     */
    @Column(name = "tokens_granted", nullable = false)
    private boolean tokensGranted;

    /**
     * Rejection reason if session was rejected.
     */
    @Column(name = "rejection_reason", length = 255)
    private String rejectionReason;

    public enum AdSessionStatus {
        PENDING,    // Session created, waiting for ad completion
        COMPLETED,  // Ad watched and verified
        EXPIRED,    // Session timed out
        REJECTED    // Verification failed
    }
}
