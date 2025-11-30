package com.orbvpn.api.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "connection_stats_aggregate")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionStatsAggregate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_id")
    private MiningServer server;

    private LocalDateTime aggregationDate;

    @Column(precision = 19, scale = 4)
    private BigDecimal totalDataTransferred;

    private Integer totalConnections;

    private Integer totalMinutes;

    @Column
    private Float averageCpuUsage;

    @Column
    private Float averageMemoryUsage;

    @Column
    private Float averageNetworkSpeed;

    private Integer averageResponseTime;

    private Integer averageLatency;

    @Column(precision = 19, scale = 8)
    private BigDecimal totalTokensCost;

    @Column(precision = 19, scale = 8)
    private BigDecimal totalTokensEarned;

    @Enumerated(EnumType.STRING)
    private AggregationPeriod period;

    public enum AggregationPeriod {
        HOURLY,
        DAILY,
        MONTHLY
    }
}
