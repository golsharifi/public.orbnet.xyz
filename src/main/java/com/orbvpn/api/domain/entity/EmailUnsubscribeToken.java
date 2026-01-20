package com.orbvpn.api.domain.entity;

import com.orbvpn.api.domain.enums.NotificationCategory;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity for managing email unsubscribe tokens.
 * These tokens provide secure, one-click unsubscribe functionality
 * compliant with CAN-SPAM and GDPR regulations.
 *
 * Each token is unique per user and can optionally target a specific
 * notification category for granular unsubscribe control.
 */
@Entity
@Data
@Table(name = "email_unsubscribe_token", indexes = {
        @Index(name = "idx_unsubscribe_token", columnList = "token"),
        @Index(name = "idx_unsubscribe_user_id", columnList = "user_id"),
        @Index(name = "idx_unsubscribe_expires_at", columnList = "expires_at")
})
@EqualsAndHashCode(exclude = {"user"})
@ToString(exclude = {"user"})
public class EmailUnsubscribeToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @NotNull
    private User user;

    @Column(name = "token", nullable = false, unique = true, length = 64)
    @NotNull
    private String token;

    @Column(name = "category")
    @Enumerated(EnumType.STRING)
    private NotificationCategory category;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "is_used", nullable = false)
    private boolean used = false;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (token == null) {
            token = generateToken();
        }
        if (expiresAt == null) {
            // Tokens expire after 1 year by default (for long-lived unsubscribe links)
            expiresAt = LocalDateTime.now().plusYears(1);
        }
    }

    public static String generateToken() {
        return UUID.randomUUID().toString().replace("-", "") +
               UUID.randomUUID().toString().replace("-", "").substring(0, 32);
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !used && !isExpired();
    }

    public void markAsUsed(String ipAddress, String userAgent) {
        this.used = true;
        this.usedAt = LocalDateTime.now();
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }

    /**
     * Creates a new unsubscribe token for a user.
     *
     * @param user The user to create the token for
     * @param category Optional category for granular unsubscribe (null for global)
     * @return A new EmailUnsubscribeToken instance
     */
    public static EmailUnsubscribeToken createForUser(User user, NotificationCategory category) {
        EmailUnsubscribeToken token = new EmailUnsubscribeToken();
        token.setUser(user);
        token.setCategory(category);
        token.setToken(generateToken());
        token.setExpiresAt(LocalDateTime.now().plusYears(1));
        return token;
    }
}
