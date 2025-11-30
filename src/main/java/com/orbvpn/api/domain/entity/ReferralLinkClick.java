package com.orbvpn.api.domain.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Tracks clicks on referral links for analytics.
 */
@Entity
@Table(name = "referral_link_click", indexes = {
    @Index(name = "idx_referral_click_code", columnList = "referral_code"),
    @Index(name = "idx_referral_click_user", columnList = "referrer_id"),
    @Index(name = "idx_referral_click_created", columnList = "created_at"),
    @Index(name = "idx_referral_click_converted", columnList = "converted")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class ReferralLinkClick {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The referral code that was clicked.
     */
    @Column(name = "referral_code", nullable = false, length = 50)
    private String referralCode;

    /**
     * The user who owns the referral code.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referrer_id", nullable = false)
    private User referrer;

    /**
     * IP address of the clicker (hashed for privacy).
     */
    @Column(name = "ip_hash", length = 64)
    private String ipHash;

    /**
     * User agent string.
     */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /**
     * Referrer URL (where the click came from).
     */
    @Column(name = "referer_url", length = 1000)
    private String refererUrl;

    /**
     * Country code from IP geolocation.
     */
    @Column(length = 2)
    private String country;

    /**
     * Whether this click resulted in a registration.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean converted = false;

    /**
     * The user ID if the click converted to registration.
     */
    @Column(name = "converted_user_id")
    private Integer convertedUserId;

    /**
     * When the conversion happened (if applicable).
     */
    @Column(name = "converted_at")
    private LocalDateTime convertedAt;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * Mark this click as converted.
     */
    public void markConverted(User convertedUser) {
        this.converted = true;
        this.convertedUserId = convertedUser.getId();
        this.convertedAt = LocalDateTime.now();
    }
}
