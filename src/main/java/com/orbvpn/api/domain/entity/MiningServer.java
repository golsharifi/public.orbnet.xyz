package com.orbvpn.api.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.hibernate.annotations.Formula;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "mining_servers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MiningServer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_id")
    private User operator;

    private String hostName;
    private String publicIp;
    private String privateIp;

    @Formula("CONCAT(city, ', ', country)")
    private String location;

    private String city;
    private String country;
    private String continent;
    private Boolean cryptoFriendly;

    @Builder.Default
    private Boolean miningEnabled = true;

    @Builder.Default
    @Column(precision = 19, scale = 8)
    private BigDecimal tokenBalance = BigDecimal.ZERO;

    @Column(name = "cpu_usage", precision = 19, scale = 4)
    private BigDecimal cpuUsage;

    @Column(name = "memory_usage", precision = 19, scale = 4)
    private BigDecimal memoryUsage;

    @Column(name = "network_speed", precision = 19, scale = 4)
    private BigDecimal networkSpeed;

    @Column(name = "active_connections")
    private Integer activeConnections;

    @Column(name = "last_heartbeat")
    private LocalDateTime lastHeartbeat;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Column(name = "max_connections")
    private Integer maxConnections;

    @Column(name = "last_reward_claim")
    private LocalDateTime lastRewardClaim;

    @Column(name = "mining_rate", precision = 19, scale = 8)
    private BigDecimal miningRate;

    @OneToMany(mappedBy = "miningServer")
    private Set<MiningServerProtocol> protocols;

}
