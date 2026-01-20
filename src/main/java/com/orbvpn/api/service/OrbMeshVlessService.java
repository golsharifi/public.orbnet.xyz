package com.orbvpn.api.service;

import com.orbvpn.api.domain.entity.OrbMeshServer;
import com.orbvpn.api.domain.entity.OrbMeshVlessConfig;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.exception.DeviceLimitExceededException;
import com.orbvpn.api.exception.SubscriptionExpiredException;
import com.orbvpn.api.repository.OrbMeshServerRepository;
import com.orbvpn.api.repository.OrbMeshVlessConfigRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing VLESS configurations.
 * VLESS is a proxy protocol (not a tunnel like WireGuard), so it doesn't
 * require IP allocation. Users are identified by UUIDs.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrbMeshVlessService {

    private final OrbMeshVlessConfigRepository configRepository;
    private final OrbMeshServerRepository serverRepository;
    private final RestTemplate restTemplate;
    private final OrbMeshSubscriptionValidationService subscriptionValidationService;

    /**
     * Get or create VLESS config for a user on a specific server.
     * This generates a new VLESS UUID if the user doesn't have one.
     *
     * @param user The user requesting the config
     * @param orbmeshServerId The target server ID
     * @return VLESS configuration
     * @throws SubscriptionExpiredException if user has no active subscription
     * @throws DeviceLimitExceededException if user has exceeded device limit
     */
    @Transactional
    public OrbMeshVlessConfig getOrCreateConfig(User user, Long orbmeshServerId) {
        // Validate subscription and device limit before proceeding
        subscriptionValidationService.validateConnectionAllowed(user);

        OrbMeshServer server = serverRepository.findById(orbmeshServerId)
                .orElseThrow(() -> new RuntimeException("OrbMesh server not found: " + orbmeshServerId));

        // Check if config already exists
        Optional<OrbMeshVlessConfig> existing = configRepository
                .findByUserUuidAndServer(user.getUuid(), server);

        if (existing.isPresent()) {
            OrbMeshVlessConfig config = existing.get();

            // If inactive, reactivate it
            if (!config.getActive()) {
                log.info("🔄 Reactivating revoked VLESS config for user {} on server {}",
                        user.getUuid(), server.getName());
                config.setActive(true);
                config.setLastConnectedAt(LocalDateTime.now());

                // Notify server to add user back
                notifyServerAddUser(server, config);

                return configRepository.save(config);
            }

            // If active, just update last connected time
            log.info("✅ Returning existing active VLESS config for user {} on server {}",
                    user.getUuid(), server.getName());
            config.setLastConnectedAt(LocalDateTime.now());
            return configRepository.save(config);
        }

        // Generate new config if none exists
        log.info("🔵 Generating new VLESS config for user {} on server {}",
                user.getUuid(), server.getName());

        String vlessUuid = UUID.randomUUID().toString();

        OrbMeshVlessConfig config = OrbMeshVlessConfig.builder()
                .userUuid(user.getUuid())
                .server(server)
                .vlessUuid(vlessUuid)
                .flow("xtls-rprx-vision")
                .encryption("none")
                .security("reality")
                .transport("tcp")
                .active(true)
                .lastConnectedAt(LocalDateTime.now())
                .build();

        config = configRepository.save(config);

        // Notify OrbMesh server to add user
        notifyServerAddUser(server, config);

        return config;
    }

    /**
     * Notify the OrbMesh server to add a VLESS user
     */
    private void notifyServerAddUser(OrbMeshServer server, OrbMeshVlessConfig config) {
        try {
            String endpoint = getServerEndpoint(server);
            String url = String.format("https://%s:8443/vless/add-user", endpoint);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + server.getApiKey());

            Map<String, String> request = Map.of(
                    "userUuid", config.getUserUuid(),
                    "email", config.getUserUuid() + "@orbvpn.com" // Placeholder email for logging
            );

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(request, headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity,
                    new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // The server returns the actual VLESS UUID it generated
                Object vlessUuid = response.getBody().get("vlessUuid");
                if (vlessUuid != null) {
                    config.setVlessUuid(vlessUuid.toString());
                    configRepository.save(config);
                    log.info("✅ VLESS user added on server {} - UUID: {}",
                            server.getName(), vlessUuid.toString().substring(0, 8) + "...");
                }
            }

        } catch (Exception e) {
            log.error("❌ Error notifying OrbMesh server to add VLESS user: {}", e.getMessage());
            // Don't throw - the config was saved, server notification is best-effort
        }
    }

    /**
     * Revoke (deactivate) a VLESS config
     */
    @Transactional
    public boolean revokeConfig(User user, Long orbmeshServerId) {
        OrbMeshServer server = serverRepository.findById(orbmeshServerId)
                .orElseThrow(() -> new RuntimeException("OrbMesh server not found: " + orbmeshServerId));

        Optional<OrbMeshVlessConfig> config = configRepository
                .findByUserUuidAndServer(user.getUuid(), server);

        if (config.isEmpty()) {
            return false;
        }

        OrbMeshVlessConfig vlessConfig = config.get();
        vlessConfig.setActive(false);
        configRepository.save(vlessConfig);

        // Notify server to remove user
        notifyServerRemoveUser(server, vlessConfig);

        log.info("✅ Revoked VLESS config for user {} on server {}",
                user.getUuid(), server.getName());

        return true;
    }

    /**
     * Notify the OrbMesh server to remove a VLESS user
     */
    private void notifyServerRemoveUser(OrbMeshServer server, OrbMeshVlessConfig config) {
        try {
            String endpoint = getServerEndpoint(server);
            String url = String.format("https://%s:8443/vless/remove-user", endpoint);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + server.getApiKey());

            Map<String, String> request = Map.of(
                    "userUuid", config.getUserUuid());

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(request, headers);

            restTemplate.postForEntity(url, entity, Void.class);

            log.info("✅ Notified OrbMesh server {} to remove VLESS user {}",
                    server.getName(), config.getUserUuid());

        } catch (Exception e) {
            log.error("❌ Error notifying server to remove VLESS user: {}", e.getMessage());
        }
    }

    /**
     * Get all VLESS configs for a user
     */
    @Transactional(readOnly = true)
    public List<OrbMeshVlessConfig> getUserConfigs(User user) {
        return configRepository.findByUserUuid(user.getUuid());
    }

    /**
     * Get active VLESS configs for a user
     */
    @Transactional(readOnly = true)
    public List<OrbMeshVlessConfig> getActiveUserConfigs(User user) {
        return configRepository.findByUserUuidAndActiveTrue(user.getUuid());
    }

    /**
     * Sync VLESS config from mobile app to backend.
     * Called after the Flutter app successfully connects to a server.
     *
     * @throws SubscriptionExpiredException if user has no active subscription
     * @throws DeviceLimitExceededException if user has exceeded device limit
     */
    @Transactional
    public OrbMeshVlessConfig syncConfig(User user, Long orbmeshServerId, String vlessUuid,
            String flow, String security, String transport) {
        // Validate subscription and device limit before proceeding
        subscriptionValidationService.validateConnectionAllowed(user);

        OrbMeshServer server = serverRepository.findById(orbmeshServerId)
                .orElseThrow(() -> new RuntimeException("OrbMesh server not found: " + orbmeshServerId));

        Optional<OrbMeshVlessConfig> existing = configRepository
                .findByUserUuidAndServer(user.getUuid(), server);

        if (existing.isPresent()) {
            OrbMeshVlessConfig config = existing.get();
            log.info("🔄 Updating existing VLESS config for user {} on server {}",
                    user.getUuid(), server.getName());
            config.setVlessUuid(vlessUuid);
            config.setFlow(flow != null ? flow : "xtls-rprx-vision");
            config.setSecurity(security != null ? security : "reality");
            config.setTransport(transport != null ? transport : "tcp");
            config.setActive(true);
            config.setLastConnectedAt(LocalDateTime.now());
            return configRepository.save(config);
        }

        // Create new config
        log.info("🔵 Creating new VLESS config for user {} on server {} (synced from mobile app)",
                user.getUuid(), server.getName());

        OrbMeshVlessConfig config = OrbMeshVlessConfig.builder()
                .userUuid(user.getUuid())
                .server(server)
                .vlessUuid(vlessUuid)
                .flow(flow != null ? flow : "xtls-rprx-vision")
                .encryption("none")
                .security(security != null ? security : "reality")
                .transport(transport != null ? transport : "tcp")
                .active(true)
                .lastConnectedAt(LocalDateTime.now())
                .build();

        return configRepository.save(config);
    }

    /**
     * Get a specific VLESS config by user and server
     */
    @Transactional(readOnly = true)
    public Optional<OrbMeshVlessConfig> getConfig(User user, Long orbmeshServerId) {
        return configRepository.findByUserUuidAndServerId(user.getUuid(), orbmeshServerId);
    }

    /**
     * Get server endpoint (hostname preferred, IP as fallback)
     */
    private String getServerEndpoint(OrbMeshServer server) {
        if (server.getHostname() != null && !server.getHostname().isEmpty()) {
            return server.getHostname();
        }
        if (server.getIpAddress() != null && !server.getIpAddress().isEmpty()) {
            log.warn("Server {} has no hostname, using IP address as fallback", server.getName());
            return server.getIpAddress();
        }
        throw new RuntimeException("Server has neither hostname nor IP address");
    }
}
