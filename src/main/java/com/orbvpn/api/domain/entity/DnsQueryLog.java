package com.orbvpn.api.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "dns_query_logs", indexes = {
    @Index(name = "idx_dns_query_user", columnList = "user_id"),
    @Index(name = "idx_dns_query_timestamp", columnList = "timestamp"),
    @Index(name = "idx_dns_query_service", columnList = "service_id"),
    @Index(name = "idx_dns_query_region", columnList = "region")
})
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class DnsQueryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "domain", nullable = false, length = 255)
    private String domain;

    @Column(name = "service_id", length = 100)
    private String serviceId;

    @Column(name = "region", length = 10)
    private String region;

    @Column(name = "response_type", nullable = false, length = 20)
    private String responseType; // PROXIED, DIRECT, BLOCKED, CACHED

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "client_ip", length = 45)
    private String clientIp;

    @CreatedDate
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    public DnsQueryLog(Integer userId, String domain, String serviceId, String region,
                       String responseType, Integer latencyMs, String clientIp) {
        this.userId = userId;
        this.domain = domain;
        this.serviceId = serviceId;
        this.region = region;
        this.responseType = responseType;
        this.latencyMs = latencyMs;
        this.clientIp = clientIp;
        this.timestamp = LocalDateTime.now();
    }
}
