package com.orbvpn.api.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "connection_stats")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionStats {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_id")
    private MiningServer server;

    @Column(nullable = false)
    private LocalDateTime connectionStart;

    private LocalDateTime connectionEnd;

    @Column(precision = 19, scale = 4)
    private BigDecimal dataTransferred;

    @Column
    private Float cpuUsage;

    @Column
    private Float memoryUsage;

    @Column
    private Float uploadSpeed;

    @Column
    private Float downloadSpeed;

    @Column
    private Float networkSpeed;

    private Integer responseTime;

    private Integer latency;

    @Column(precision = 19, scale = 8)
    private BigDecimal tokensCost;

    @Column(precision = 19, scale = 8)
    private BigDecimal tokensEarned;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
