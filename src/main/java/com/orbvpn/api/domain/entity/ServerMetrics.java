package com.orbvpn.api.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "server_metrics")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerMetrics {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "server_id")
    private MiningServer server;

    @Column(precision = 19, scale = 4)
    private BigDecimal cpuUsage;

    @Column(precision = 19, scale = 4)
    private BigDecimal memoryUsage;

    @Column(precision = 19, scale = 4)
    private BigDecimal uploadSpeed;

    @Column(precision = 19, scale = 4)
    private BigDecimal downloadSpeed;

    private Integer activeConnections;
    private Integer maxConnections;

    @Column(precision = 19, scale = 4)
    private BigDecimal uptime;

    private Integer responseTime;
    private Integer latency;

    @Column(precision = 10, scale = 4)
    private BigDecimal packetLoss;

    @Column(precision = 10, scale = 4)
    private BigDecimal connectionStability;

    private LocalDateTime lastCheck;

    @Column(precision = 19, scale = 4)
    private BigDecimal dataTransferred;

    @Column(name = "network_speed", precision = 19, scale = 4)
    private BigDecimal networkSpeed;

    @Column(name = "created_at")
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
