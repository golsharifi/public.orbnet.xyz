package com.orbvpn.api.domain.entity;

import com.orbvpn.api.domain.enums.DnsServiceType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "dns_user_rules", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "service_id", "service_type"})
})
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class DnsUserRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "service_id", nullable = false, length = 100)
    private String serviceId;

    @Column(name = "service_name", length = 200)
    private String serviceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false, length = 20)
    private DnsServiceType serviceType;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "preferred_region", length = 10)
    private String preferredRegion;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public DnsUserRule(User user, String serviceId, String serviceName,
                       DnsServiceType serviceType, boolean enabled, String preferredRegion) {
        this.user = user;
        this.serviceId = serviceId;
        this.serviceName = serviceName;
        this.serviceType = serviceType;
        this.enabled = enabled;
        this.preferredRegion = preferredRegion;
    }
}
