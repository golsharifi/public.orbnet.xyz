package com.orbvpn.api.domain.entity;

import lombok.Getter;
import lombok.Setter;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import com.orbvpn.api.domain.enums.GatewayName;

@Entity
@Getter
@Setter
@Table(name = "subscription_history")
public class SubscriptionHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "user_email")
    private String userEmail;

    @Column(name = "archived")
    private Boolean archived = false;

    @Column(name = "device_id")
    private String deviceId;

    @Column(nullable = false)
    private String platform;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private UserSubscription subscription;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "subscription_id_str")
    private String subscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GatewayName gateway;

    @Column(name = "is_trial")
    private Boolean isTrial;

    @Column(name = "was_refunded")
    private Boolean wasRefunded = false;

    @Column(name = "refund_date")
    private LocalDateTime refundDate;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public void archiveForDeletedUser() {
        if (this.user != null) {
            this.userEmail = this.user.getEmail();
            this.user = null;
            this.archived = true;
        }
    }

    public void archive() {
        this.archived = true;
        this.subscription = null;
    }

    // Add helper methods for managing the relationship
    public void setSubscription(UserSubscription subscription) {
        this.subscription = subscription;
        if (subscription != null && !subscription.getSubscriptionHistories().contains(this)) {
            subscription.getSubscriptionHistories().add(this);
        }
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }
}
