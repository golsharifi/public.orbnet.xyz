// src/main/java/com/orbvpn/api/service/OrbXServerService.java
package com.orbvpn.api.service;

import com.orbvpn.api.domain.dto.*;
import com.orbvpn.api.domain.entity.OrbXServer;
import com.orbvpn.api.exception.BadRequestException;
import com.orbvpn.api.exception.NotFoundException;
import com.orbvpn.api.repository.OrbXServerRepository;
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
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrbXServerService {

    private final OrbXServerRepository serverRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private static final SecureRandom secureRandom = new SecureRandom();

    // ✅ Inject JWT secret from application.yml
    @Value("${jwt.secret}")
    private String sharedJwtSecret;

    @Transactional
    public OrbXServerRegistrationResult registerServer(OrbXServerInput input) {
        log.info("🔵 Registering new OrbX server: {}", input.getName());

        // Validation
        if (serverRepository.findByName(input.getName()).isPresent()) {
            throw new BadRequestException("Server with name '" + input.getName() + "' already exists");
        }

        if (serverRepository.findByRegion(input.getRegion()).isPresent()) {
            throw new BadRequestException("Server with region '" + input.getRegion() + "' already exists");
        }

        OrbXServer server = new OrbXServer();
        mapInputToEntity(input, server);

        // ✅ Generate unique API key (for server-to-OrbNet authentication)
        String apiKey = generateApiKey();

        // ✅ Use SHARED JWT secret from Spring configuration (for validating user
        // tokens)
        server.setApiKey(apiKey);
        server.setApiKeyHash(passwordEncoder.encode(apiKey));
        server.setJwtSecret(this.sharedJwtSecret); // ✅ Store SHARED secret from application.yml
        server.setQuantumSafe(true);
        server.setOnline(false);
        server.setEnabled(true);

        OrbXServer savedServer = serverRepository.save(server);
        log.info("✅ Successfully registered OrbX server with ID: {} using SHARED JWT secret", savedServer.getId());

        return OrbXServerRegistrationResult.builder()
                .server(mapEntityToView(savedServer))
                .apiKey(apiKey)
                .jwtSecret(this.sharedJwtSecret) // ✅ Return SHARED secret from application.yml
                .build();
    }

    @Transactional
    public OrbXServerView updateServer(Long id, OrbXServerInput input) {
        log.info("Updating OrbX server: {}", id);

        OrbXServer server = serverRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("OrbX server not found: " + id));

        if (!server.getName().equals(input.getName())) {
            serverRepository.findByName(input.getName()).ifPresent(existing -> {
                throw new BadRequestException("Server with name '" + input.getName() + "' already exists");
            });
        }

        mapInputToEntity(input, server);
        OrbXServer updatedServer = serverRepository.save(server);

        log.info("✅ Successfully updated OrbX server: {}", id);
        return mapEntityToView(updatedServer);
    }

    @Transactional
    public Boolean deleteServer(Long id) {
        log.info("Deleting OrbX server: {}", id);

        OrbXServer server = serverRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("OrbX server not found: " + id));

        serverRepository.delete(server);
        log.info("✅ Successfully deleted OrbX server: {}", id);
        return true;
    }

    @Transactional
    public OrbXServerRegistrationResult regenerateApiKey(Long serverId) {
        log.info("Regenerating API key for OrbX server: {}", serverId);

        OrbXServer server = serverRepository.findById(serverId)
                .orElseThrow(() -> new NotFoundException("OrbX server not found: " + serverId));

        String newApiKey = generateApiKey();

        // ✅ Keep using SHARED JWT secret (don't regenerate it!)
        server.setApiKey(newApiKey);
        server.setApiKeyHash(passwordEncoder.encode(newApiKey));
        server.setJwtSecret(this.sharedJwtSecret); // ✅ Keep SHARED secret from application.yml

        OrbXServer updatedServer = serverRepository.save(server);
        log.info("✅ Successfully regenerated API key for server: {}", serverId);

        return OrbXServerRegistrationResult.builder()
                .server(mapEntityToView(updatedServer))
                .apiKey(newApiKey)
                .jwtSecret(this.sharedJwtSecret) // ✅ Return SHARED secret from application.yml
                .build();
    }

    @Transactional
    public OrbXServerView updateServerStatus(Long serverId, Boolean online) {
        log.info("Updating server status - ID: {}, Online: {}", serverId, online);

        OrbXServer server = serverRepository.findById(serverId)
                .orElseThrow(() -> new NotFoundException("OrbX server not found: " + serverId));

        server.setOnline(online);
        server.setLastHeartbeat(LocalDateTime.now());

        OrbXServer updatedServer = serverRepository.save(server);
        return mapEntityToView(updatedServer);
    }

    @Transactional
    public OrbXServerView updateServerMetrics(Long serverId, OrbXServerMetricsInput metrics) {
        log.info("💓 Updating server metrics - ID: {}", serverId);

        OrbXServer server = serverRepository.findById(serverId)
                .orElseThrow(() -> new NotFoundException("OrbX server not found: " + serverId));

        server.setCurrentConnections(metrics.getCurrentConnections());
        server.setCpuUsage(metrics.getCpuUsage());
        server.setMemoryUsage(metrics.getMemoryUsage());
        server.setLatencyMs(metrics.getLatencyMs());
        server.setLastHeartbeat(LocalDateTime.now());
        server.setOnline(true); // Mark online when receiving heartbeat

        OrbXServer updatedServer = serverRepository.save(server);
        return mapEntityToView(updatedServer);
    }

    public OrbXConfig getServerConfig(Long serverId) {
        OrbXServer server = serverRepository.findById(serverId)
                .orElseThrow(() -> new NotFoundException("OrbX server not found: " + serverId));

        String endpoint = (server.getHostname() != null && !server.getHostname().isEmpty())
                ? server.getHostname()
                : server.getIpAddress();

        return OrbXConfig.builder()
                .serverId(server.getId())
                .endpoint(endpoint)
                .port(server.getPort())
                .publicKey(server.getPublicKey())
                .protocols(server.getProtocolsList()) // ✅ Use getProtocolsList()
                .tlsFingerprint(server.getTlsFingerprint())
                .quantumSafe(server.getQuantumSafe() != null ? server.getQuantumSafe() : true)
                .region(server.getRegion())
                .build();
    }

    public List<OrbXServerView> getAllServers() {
        try {
            List<OrbXServer> servers = serverRepository.findAll();

            if (servers == null) {
                return Collections.emptyList();
            }

            return servers.stream()
                    .map(this::mapEntityToView)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error getting all servers", e);
            return Collections.emptyList();
        }
    }

    public boolean validateApiKey(String apiKey) {
        return serverRepository.findAll().stream()
                .anyMatch(server -> passwordEncoder.matches(apiKey, server.getApiKeyHash()));
    }

    private void mapInputToEntity(OrbXServerInput input, OrbXServer server) {
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

    private OrbXServerView mapEntityToView(OrbXServer server) {
        if (server == null) {
            return null;
        }

        OrbXServerView view = new OrbXServerView();
        view.setId(server.getId());
        view.setName(server.getName());
        view.setRegion(server.getRegion());
        view.setHostname(server.getHostname() != null ? server.getHostname() : server.getIpAddress());
        view.setIpAddress(server.getIpAddress());
        view.setPort(server.getPort());
        view.setLocation(server.getLocation());
        view.setCountry(server.getCountry());
        view.setCountryCode(server.getCountryCode());
        view.setProtocols(server.getProtocolsList()); // ✅ Use getProtocolsList() not getProtocols()
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
        view.setCreatedAt(server.getCreatedAt());
        view.setUpdatedAt(server.getUpdatedAt());
        return view;
    }

    private String generateApiKey() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return "orbx_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateJwtSecret() {
        byte[] bytes = new byte[64];
        secureRandom.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}