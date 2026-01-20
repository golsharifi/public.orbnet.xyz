package com.orbvpn.api.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.orbvpn.api.domain.enums.ConnectionStatus;

@Entity
@Table(name = "connection_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_id")
    private MiningServer server;

    private LocalDateTime timestamp;
    private BigDecimal dataTransferred;
    private Integer latency;
    private BigDecimal throughput;
    private BigDecimal packetLoss;
    private BigDecimal protocolOverhead;

    @Enumerated(EnumType.STRING)
    private ConnectionStatus status;
}
