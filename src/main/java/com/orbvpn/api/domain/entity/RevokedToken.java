package com.orbvpn.api.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Entity to store revoked/blacklisted JWT tokens.
 * Tokens are stored until their natural expiration, after which they can be cleaned up.
 */
@Entity
@Table(name = "revoked_token", indexes = {
    @Index(name = "idx_revoked_token_jti", columnList = "jti"),
    @Index(name = "idx_revoked_token_user_id", columnList = "user_id"),
    @Index(name = "idx_revoked_token_expires_at", columnList = "expires_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevokedToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * JWT token identifier - hash of the token for lookup.
     * We don't store the full token for security reasons.
     */
    @Column(name = "jti", nullable = false, unique = true, length = 64)
    private String jti;

    /**
     * User ID who owned this token.
     */
    @Column(name = "user_id")
    private Integer userId;

    /**
     * Username for audit purposes.
     */
    @Column(name = "username", length = 255)
    private String username;

    /**
     * Token type: "access" or "refresh".
     */
    @Column(name = "token_type", length = 20)
    private String tokenType;

    /**
     * When the token was revoked.
     */
    @CreationTimestamp
    @Column(name = "revoked_at", nullable = false)
    private LocalDateTime revokedAt;

    /**
     * When the token would have naturally expired.
     * Used for cleanup - tokens can be deleted after this date.
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * Reason for revocation.
     */
    @Column(name = "reason", length = 100)
    private String reason;

    /**
     * IP address from which the revocation was initiated (for audit).
     */
    @Column(name = "revoked_from_ip", length = 45)
    private String revokedFromIp;
}
