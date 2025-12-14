package com.orbvpn.api.domain.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Entity for storing magic link tokens for passwordless login.
 * Users can request a magic link sent to their email to login without a password.
 * Unlike magic login codes (6-digit), these are URL-based tokens that can be clicked directly.
 */
@Entity
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
@Table(name = "magic_link_token")
public class MagicLinkToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    @CreatedDate
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean used = false;

    /**
     * Check if this token has expired
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Check if this token is valid (not used and not expired)
     */
    public boolean isValid() {
        return !used && !isExpired();
    }
}
