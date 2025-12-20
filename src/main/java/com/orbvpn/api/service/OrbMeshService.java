package com.orbvpn.api.service;

import com.orbvpn.api.domain.dto.*;
import com.orbvpn.api.domain.entity.OrbMeshServer;
import com.orbvpn.api.domain.enums.SortType;
import com.orbvpn.api.exception.BadRequestException;
import com.orbvpn.api.exception.NotFoundException;
import com.orbvpn.api.repository.OrbMeshServerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrbMeshService {

    private final OrbMeshServerRepository serverRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private static final SecureRandom secureRandom = new SecureRandom();

    // ✅ Inject JWT secret from application.yml
    @Value("${jwt.secret}")
    private String sharedJwtSecret;

    public List<OrbMeshServerView> getOrbMeshServers(SortType sortBy, Boolean ascending) {
        log.info("Getting OrbMesh servers - sortBy: {}, ascending: {}", sortBy, ascending);

        try {
            List<OrbMeshServer> servers = serverRepository.findByOnlineTrueAndEnabledTrue();

            if (servers == null || servers.isEmpty()) {
                log.info("No online servers found");
                return Collections.emptyList();
            }

            if (sortBy != null) {
                Comparator<OrbMeshServer> comparator = getComparator(sortBy);
                if (ascending != null && !ascending) {
                    comparator = comparator.reversed();
                }
                servers = servers.stream().sorted(comparator).toList();
            }

            return servers.stream()
                    .map(this::mapEntityToView)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error getting OrbMesh servers", e);
            return Collections.emptyList();
        }
    }

    public OrbMeshConfig getOrbMeshConfig(Long serverId) {
        log.info("Getting OrbMesh config for server: {}", serverId);

        OrbMeshServer server = serverRepository.findById(serverId)
                .orElseThrow(() -> new NotFoundException("OrbMesh server not found: " + serverId));

        // Use hostname as endpoint, fallback to IP
        String endpoint = (server.getHostname() != null && !server.getHostname().isEmpty())
                ? server.getHostname()
                : server.getIpAddress();

        return OrbMeshConfig.builder()
                .serverId(server.getId())
                .endpoint(endpoint)
                .port(server.getPort())
                .publicKey(server.getPublicKey())
                .protocols(server.getProtocolsList()) // ✅ Use JSON parser
                .tlsFingerprint(server.getTlsFingerprint())
                .quantumSafe(server.getQuantumSafe() != null ? server.getQuantumSafe() : true)
                .region(server.getRegion())
                .build();
    }

    public OrbMeshServerView getBestOrbMeshServer() {
        log.info("Finding best OrbMesh server");

        List<OrbMeshServer> servers = serverRepository.findServersWithCapacity();

        if (servers == null || servers.isEmpty()) {
            throw new NotFoundException("No available OrbMesh servers");
        }

        OrbMeshServer bestServer = servers.stream()
                .min(Comparator
                        .comparingInt(OrbMeshServer::getCurrentConnections)
                        .thenComparingInt(s -> s.getLatencyMs() != null ? s.getLatencyMs() : 999))
                .orElseThrow(() -> new NotFoundException("No available servers"));

        return mapEntityToView(bestServer);
    }

    @Transactional
    public OrbMeshServerRegistrationResult registerServer(OrbMeshServerInput input) {
        log.info("🔵 Registering new OrbMesh server: {}", input.getName());

        // Validation
        if (serverRepository.findByName(input.getName()).isPresent()) {
            throw new BadRequestException("Server with name '" + input.getName() + "' already exists");
        }

        if (serverRepository.findByRegion(input.getRegion()).isPresent()) {
            throw new BadRequestException("Server with region '" + input.getRegion() + "' already exists");
        }

        OrbMeshServer server = new OrbMeshServer();
        mapInputToEntity(input, server);

        // ✅ Generate unique API key (for server-to-OrbNet authentication)
        String apiKey = generateApiKey();

        // ✅ Use SHARED JWT secret from Spring configuration (for validating user
        // tokens)
        server.setApiKey(apiKey);
        server.setApiKeyHash(passwordEncoder.encode(apiKey));
        server.setJwtSecret(this.sharedJwtSecret);
        server.setQuantumSafe(true);
        server.setOnline(false);
        server.setEnabled(true);

        OrbMeshServer savedServer = serverRepository.save(server);
        log.info("✅ Successfully registered OrbMesh server with ID: {} using SHARED JWT secret", savedServer.getId());

        return OrbMeshServerRegistrationResult.builder()
                .server(mapEntityToView(savedServer))
                .apiKey(apiKey)
                .jwtSecret(this.sharedJwtSecret) // ✅ Return SHARED secret from application.yml
                .build();
    }

    @Transactional
    public OrbMeshServerView updateServer(Long id, OrbMeshServerInput input) {
        log.info("Updating OrbMesh server: {}", id);

        OrbMeshServer server = serverRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("OrbMesh server not found: " + id));

        if (!server.getName().equals(input.getName())) {
            serverRepository.findByName(input.getName()).ifPresent(existing -> {
                throw new BadRequestException("Server with name '" + input.getName() + "' already exists");
            });
        }

        mapInputToEntity(input, server);
        OrbMeshServer updatedServer = serverRepository.save(server);

        log.info("✅ Successfully updated OrbMesh server: {}", id);
        return mapEntityToView(updatedServer);
    }

    @Transactional
    public Boolean deleteServer(Long id) {
        log.info("Deleting OrbMesh server: {}", id);

        OrbMeshServer server = serverRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("OrbMesh server not found: " + id));

        serverRepository.delete(server);
        log.info("✅ Successfully deleted OrbMesh server: {}", id);
        return true;
    }

    @Transactional
    public OrbMeshServerRegistrationResult regenerateApiKey(Long serverId) {
        log.info("Regenerating API key for OrbMesh server: {}", serverId);

        OrbMeshServer server = serverRepository.findById(serverId)
                .orElseThrow(() -> new NotFoundException("OrbMesh server not found: " + serverId));

        String newApiKey = generateApiKey();

        // ✅ Keep using SHARED JWT secret (don't regenerate it!)
        server.setApiKey(newApiKey);
        server.setApiKeyHash(passwordEncoder.encode(newApiKey));
        server.setJwtSecret(this.sharedJwtSecret); // ✅ Keep SHARED secret from application.yml

        OrbMeshServer updatedServer = serverRepository.save(server);
        log.info("✅ Successfully regenerated API key for server: {}", serverId);

        return OrbMeshServerRegistrationResult.builder()
                .server(mapEntityToView(updatedServer))
                .apiKey(newApiKey)
                .jwtSecret(this.sharedJwtSecret) // ✅ Return SHARED secret from application.yml
                .build();
    }

    @Transactional
    public OrbMeshServerView updateServerStatus(Long serverId, Boolean online) {
        log.info("Updating server status - ID: {}, Online: {}", serverId, online);

        OrbMeshServer server = serverRepository.findById(serverId)
                .orElseThrow(() -> new NotFoundException("OrbMesh server not found: " + serverId));

        server.setOnline(online);
        server.setLastHeartbeat(LocalDateTime.now());

        OrbMeshServer updatedServer = serverRepository.save(server);
        return mapEntityToView(updatedServer);
    }

    @Transactional
    public OrbMeshServerView updateServerMetrics(Long serverId, OrbMeshServerMetricsInput metrics) {
        log.info("💓 Updating server metrics - ID: {}", serverId);

        OrbMeshServer server = serverRepository.findById(serverId)
                .orElseThrow(() -> new NotFoundException("OrbMesh server not found: " + serverId));

        server.setCurrentConnections(metrics.getCurrentConnections());
        server.setCpuUsage(metrics.getCpuUsage());
        server.setMemoryUsage(metrics.getMemoryUsage());
        server.setLatencyMs(metrics.getLatencyMs());
        server.setLastHeartbeat(LocalDateTime.now());
        server.setOnline(true); // ✅ Mark as online when receiving heartbeat

        OrbMeshServer updatedServer = serverRepository.save(server);
        log.info("✅ Metrics updated - Connections: {}, CPU: {}%, Memory: {}%",
                metrics.getCurrentConnections(), metrics.getCpuUsage(), metrics.getMemoryUsage());

        return mapEntityToView(updatedServer);
    }

    /**
     * Get ALL servers (admin only - includes offline/disabled)
     */
    public List<OrbMeshServerView> getAllServers() {
        log.info("📊 Getting ALL OrbMesh servers (admin)");

        try {
            List<OrbMeshServer> servers = serverRepository.findAll();

            if (servers == null) {
                log.warn("⚠️ Repository returned null");
                return Collections.emptyList();
            }

            log.info("✅ Found {} servers in database", servers.size());

            return servers.stream()
                    .map(server -> {
                        try {
                            return mapEntityToView(server);
                        } catch (Exception e) {
                            log.error("❌ Failed to map server: {}", server.getName(), e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("❌ Failed to fetch all servers", e);
            return Collections.emptyList();
        }
    }

    public boolean validateApiKey(String apiKey) {
        return serverRepository.findAll().stream()
                .anyMatch(server -> passwordEncoder.matches(apiKey, server.getApiKeyHash()));
    }

    private Comparator<OrbMeshServer> getComparator(SortType sortBy) {
        if (sortBy == null) {
            return Comparator.comparing(OrbMeshServer::getId);
        }

        return switch (sortBy) {
            case LOCATION -> Comparator.comparing(OrbMeshServer::getLocation);
            case CONTINENTAL -> Comparator.comparing(OrbMeshServer::getRegion)
                    .thenComparing(OrbMeshServer::getCountry);
            case CRYPTO_FRIENDLY -> Comparator.comparing(OrbMeshServer::getCountry)
                    .thenComparing(OrbMeshServer::getLocation);
            case CONNECTIONS -> Comparator.comparingInt(OrbMeshServer::getCurrentConnections);
            default -> Comparator.comparing(OrbMeshServer::getId); // ✅ Fallback case

        };
    }

    private void mapInputToEntity(OrbMeshServerInput input, OrbMeshServer server) {
        server.setName(input.getName());
        server.setRegion(input.getRegion());
        server.setHostname(input.getHostname());
        server.setIpAddress(input.getIpAddress());
        server.setPort(input.getPort());
        server.setLocation(input.getLocation());
        server.setCountry(input.getCountry().toUpperCase());
        server.setMaxConnections(input.getMaxConnections() != null ? input.getMaxConnections() : 1000);
        server.setBandwidthLimitMbps(input.getBandwidthLimitMbps());
        server.setPublicKey(input.getPublicKey());
        server.setTlsCertificate(input.getTlsCertificate());

        // ✅ Convert protocols List to JSON string
        server.setProtocolsList(input.getProtocols());
    }

    private OrbMeshServerView mapEntityToView(OrbMeshServer server) {
        if (server == null) {
            return null;
        }

        OrbMeshServerView view = new OrbMeshServerView();
        view.setId(server.getId());
        // Ensure non-null fields have defaults for GraphQL schema compliance
        view.setName(server.getName() != null ? server.getName() : "Unknown");
        view.setRegion(server.getRegion() != null ? server.getRegion() : "Unknown");
        view.setHostname(server.getHostname() != null ? server.getHostname() : server.getIpAddress());
        view.setIpAddress(server.getIpAddress());
        view.setPort(server.getPort() != null ? server.getPort() : 443);
        view.setLocation(server.getLocation() != null ? server.getLocation() : "Unknown");
        view.setCountry(server.getCountry() != null ? server.getCountry() : "Unknown");
        view.setCountryCode(server.getCountryCode());
        // Ensure protocols is never null - GraphQL expects [String!]!
        List<String> protocols = server.getProtocolsList();
        view.setProtocols(protocols != null ? protocols : Collections.emptyList());
        view.setQuantumSafe(server.getQuantumSafe() != null ? server.getQuantumSafe() : true);
        view.setOnline(server.getOnline() != null ? server.getOnline() : false);
        view.setEnabled(server.getEnabled() != null ? server.getEnabled() : true);
        view.setCurrentConnections(server.getCurrentConnections() != null ? server.getCurrentConnections() : 0);
        view.setMaxConnections(server.getMaxConnections() != null ? server.getMaxConnections() : 1000);
        view.setBandwidthLimitMbps(server.getBandwidthLimitMbps());
        view.setCpuUsage(server.getCpuUsage());
        view.setMemoryUsage(server.getMemoryUsage());
        view.setLatencyMs(server.getLatencyMs());
        view.setVersion(server.getVersion());
        view.setPublicKey(server.getPublicKey());
        view.setWireguardPort(server.getWireguardPort());
        view.setWireguardPublicKey(server.getWireguardPublicKey());
        view.setTlsFingerprint(server.getTlsFingerprint());
        view.setLastHeartbeat(server.getLastHeartbeat());
        // Ensure createdAt/updatedAt are never null - GraphQL expects String!
        view.setCreatedAt(server.getCreatedAt() != null ? server.getCreatedAt() : LocalDateTime.now());
        view.setUpdatedAt(server.getUpdatedAt() != null ? server.getUpdatedAt() : LocalDateTime.now());
        return view;
    }

    private String generateApiKey() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return "orbmesh_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}