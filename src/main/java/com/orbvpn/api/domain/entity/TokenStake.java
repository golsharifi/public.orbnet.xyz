package com.orbvpn.api.domain.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
public class TokenStake {

    @ManyToOne
    @JoinColumn(name = "staking_config") // Add this to specify the column name
    private TokenStakingConfig stakingConfig;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Rest of your fields remain the same
    @ManyToOne
    private User user;

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    @Column
    private LocalDateTime stakedAt;

    @Column
    private LocalDateTime unstakedAt;

    @Column
    private Integer lockPeriodDays;

    @Column(precision = 19, scale = 4)
    private BigDecimal rewardRate;

    @Column
    @CreatedDate
    private LocalDateTime createdAt;

    @Column
    @LastModifiedDate
    private LocalDateTime updatedAt;
}