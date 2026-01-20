package com.orbvpn.api.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.orbvpn.api.domain.enums.MiningStatus;

@Entity
@Table(name = "mining_activities")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MiningActivity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_id")
    private MiningServer server;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal dataTransferred; // GB
    private BigDecimal connectionStability; // 0-1
    private BigDecimal protocolEfficiency; // 0-1
    private BigDecimal rewardEarned;
    private Boolean isActive;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @Enumerated(EnumType.STRING)
    private MiningStatus status;

    public MiningStatus getStatus() {
        return status;
    }
}