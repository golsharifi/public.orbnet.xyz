package com.orbvpn.api.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "dns_config")
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class DnsConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "primary_dns", nullable = false, length = 45)
    private String primaryDns;

    @Column(name = "secondary_dns", length = 45)
    private String secondaryDns;

    @Column(name = "doh_enabled", nullable = false)
    private boolean dohEnabled = true;

    @Column(name = "doh_endpoint", length = 255)
    private String dohEndpoint;

    @Column(name = "sni_proxy_enabled", nullable = false)
    private boolean sniProxyEnabled = true;

    @Column(name = "max_whitelisted_ips", nullable = false)
    private int maxWhitelistedIps = 10;

    @Column(name = "whitelist_expiry_days", nullable = false)
    private int whitelistExpiryDays = 30;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Singleton pattern - there should be only one config row
    public static DnsConfig getDefault() {
        DnsConfig config = new DnsConfig();
        config.setEnabled(true);
        config.setPrimaryDns("10.8.0.1"); // WireGuard DNS
        config.setSecondaryDns("10.10.0.1"); // OpenConnect DNS
        config.setDohEnabled(true);
        config.setDohEndpoint("https://dns.orbvpn.com/dns-query");
        config.setSniProxyEnabled(true);
        config.setMaxWhitelistedIps(10);
        config.setWhitelistExpiryDays(30);
        return config;
    }
}
