package com.orbvpn.api.domain.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.orbvpn.api.domain.enums.GatewayName;

@Entity
@Table(name = "trial_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class TrialHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column
    private String deviceId;

    @Column(nullable = false)
    private String platform;

    @Column(nullable = false)
    private LocalDateTime trialStartDate;

    @Column
    private LocalDateTime trialEndDate;

    @Column(nullable = false)
    private String subscriptionId;

    @Column(nullable = false)
    private String transactionId;

    @Column(name = "product_id")
    private String productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "gateway")
    private GatewayName gateway;

    @Column
    @Builder.Default
    private Boolean completed = false;

    @Column
    private String cancelReason;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Version
    private Long version;
}