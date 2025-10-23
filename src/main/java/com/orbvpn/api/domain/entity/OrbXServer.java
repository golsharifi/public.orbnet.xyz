package com.orbvpn.api.domain.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "orbx_servers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class OrbXServer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false, unique = true)
    private String region;

    @Column(name = "hostname")
    private String hostname;

    @Column(name = "ip_address", nullable = false)
    private String ipAddress;

    @Column(nullable = false)
    private Integer port = 8443;

    @Column(nullable = false)
    private String location;

    @Column
    private String country;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "public_key")
    private String publicKey;

    @Column(name = "wireguard_port")
    private Integer wireguardPort;

    @Column(name = "wireguard_public_key")
    private String wireguardPublicKey;

    @Column(name = "api_key", nullable = false, unique = true)
    private String apiKey;

    @Column(name = "api_key_hash", nullable = false)
    private String apiKeyHash;

    @Column(name = "jwt_secret", nullable = false)
    private String jwtSecret;

    // ✅ CRITICAL FIX: Store protocols as JSON string in VARCHAR(255)
    @Column(name = "protocols", nullable = false, length = 255)
    private String protocols = "[]";

    @Column(name = "quantum_safe", nullable = false)
    private Boolean quantumSafe = true;

    @Column(nullable = false)
    private Boolean online = false;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "current_connections", nullable = false)
    private Integer currentConnections = 0;

    @Column(name = "max_connections", nullable = false)
    private Integer maxConnections = 1000;

    @Column(name = "bandwidth_limit_mbps")
    private Integer bandwidthLimitMbps;

    @Column(name = "cpu_usage")
    private Double cpuUsage;

    @Column(name = "memory_usage")
    private Double memoryUsage;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column
    private String version;

    @Column(name = "tls_certificate", columnDefinition = "TEXT")
    private String tlsCertificate;

    @Column(name = "tls_fingerprint")
    private String tlsFingerprint;

    @Column(name = "last_heartbeat")
    private LocalDateTime lastHeartbeat;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ✅ JSON converter for protocols
    @Transient
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @OneToMany(mappedBy = "server", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrbXConnectionStats> connectionStats;

    /**
     * Get protocols as List by parsing JSON string from DB
     * DB format: ["teams","doh","https","shaparak"]
     */
    public List<String> getProtocolsList() {
        if (protocols == null || protocols.isEmpty() || protocols.equals("[]")) {
            return Collections.emptyList();
        }

        try {
            List<String> list = objectMapper.readValue(protocols, new TypeReference<List<String>>() {
            });
            return list != null ? list : Collections.emptyList();
        } catch (JsonProcessingException e) {
            log.error("Failed to parse protocols JSON: {}", protocols, e);
            return Collections.emptyList();
        }
    }

    /**
     * Set protocols from List by converting to JSON string for DB
     * Converts to: ["teams","doh","https"]
     */
    public void setProtocolsList(List<String> protocolList) {
        if (protocolList == null || protocolList.isEmpty()) {
            this.protocols = "[]";
            return;
        }

        try {
            this.protocols = objectMapper.writeValueAsString(protocolList);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize protocols to JSON", e);
            this.protocols = "[]";
        }
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();

        // Set defaults
        if (online == null)
            online = false;
        if (enabled == null)
            enabled = true;
        if (quantumSafe == null)
            quantumSafe = true;
        if (currentConnections == null)
            currentConnections = 0;
        if (maxConnections == null)
            maxConnections = 1000;
        if (protocols == null || protocols.isEmpty())
            protocols = "[]";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}