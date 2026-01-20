package com.orbvpn.api.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_extra_logins")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class UserExtraLogins {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private ExtraLoginsPlan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gifted_by_id")
    private User giftedBy;

    @Column(nullable = false)
    private LocalDateTime startDate;

    private LocalDateTime expiryDate;

    @Column(nullable = false)
    private int loginCount;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private boolean subscription;

    @Column(name = "subscription_id")
    private String subscriptionId;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}