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
@Table(name = "dns_whitelisted_ips", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "ip_address"})
})
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class DnsWhitelistedIp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "label", length = 100)
    private String label;

    @Column(name = "device_type", length = 50)
    private String deviceType;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "last_used")
    private LocalDateTime lastUsed;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    public DnsWhitelistedIp(User user, String ipAddress, String label, String deviceType) {
        this.user = user;
        this.ipAddress = ipAddress;
        this.label = label;
        this.deviceType = deviceType;
        this.active = true;
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return active && !isExpired();
    }
}
