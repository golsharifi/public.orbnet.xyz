package com.orbvpn.api.service;

import com.orbvpn.api.domain.entity.OrbMeshServer;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.OrbMeshWireGuardConfig;
import com.orbvpn.api.domain.entity.OrbMeshWireGuardIPPool;
import com.orbvpn.api.exception.DeviceLimitExceededException;
import com.orbvpn.api.exception.SubscriptionExpiredException;
import com.orbvpn.api.repository.OrbMeshServerRepository;
import com.orbvpn.api.repository.OrbMeshWireGuardConfigRepository;
import com.orbvpn.api.repository.OrbMeshWireGuardIPPoolRepository;
import com.orbvpn.api.utils.WireGuardUtil;

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

@Service
@Slf4j
@RequiredArgsConstructor
public class OrbMeshWireGuardService {

    private final OrbMeshWireGuardConfigRepository configRepository;
    private final OrbMeshWireGuardIPPoolRepository ipPoolRepository;
    private final OrbMeshServerRepository serverRepository;
    private final RestTemplate restTemplate;
    private final OrbMeshSubscriptionValidationService subscriptionValidationService;

    /**
     * Get all OrbMesh servers (for app caching)
     */
    public List<OrbMeshServer> getAllServers() {
        return serverRepository.findByOnlineTrueAndEnabledTrue();
    }

    /**
     * Get or create WireGuard config for specific server.
     *
     * @param user The user requesting the config
     * @param orbmeshServerId The target server ID
     * @return WireGuard configuration
     * @throws SubscriptionExpiredException if user has no active subscription
     * @throws DeviceLimitExceededException if user has exceeded device limit
     */
    @Transactional
    public OrbMeshWireGuardConfig getOrCreateConfig(User user, Long orbmeshServerId) {
        // Validate subscription and device limit before proceeding
        subscriptionValidationService.validateConnectionAllowed(user);

        OrbMeshServer server = serverRepository.findById(orbmeshServerId)
                .orElseThrow(() -> new RuntimeException("OrbMesh server not found: " + orbmeshServerId));

        // Check if config already exists
        Optional<OrbMeshWireGuardConfig> existing = configRepository
                .findByUserUuidAndServer(user.getUuid(), server);

        if (existing.isPresent()) {
            OrbMeshWireGuardConfig config = existing.get();

            // ✅ If inactive, reactivate it
            if (!config.getActive()) {
                log.info("🔄 Reactivating revoked WireGuard config for user {} on server {}",
                        user.getUuid(), server.getName());
                config.setActive(true);
                config.setLastConnectedAt(LocalDateTime.now());

                // Notify server to add peer back
                notifyServerAddPeer(server, config);

                return configRepository.save(config);
            }

            // ✅ If active, just update last connected time
            log.info("✅ Returning existing active WireGuard config for user {} on server {}",
                    user.getUuid(), server.getName());
            config.setLastConnectedAt(LocalDateTime.now());
            return configRepository.save(config);
        }

        // Generate new config if none exists
        log.info("🔵 Generating new WireGuard config for user {} on server {}",
                user.getUuid(), server.getName());

        WireGuardUtil.KeyPair keyPair = WireGuardUtil.generateKeyPair();
        String allocatedIP = allocateIP(orbmeshServerId);

        OrbMeshWireGuardConfig config = OrbMeshWireGuardConfig.builder()
                .userUuid(user.getUuid())
                .server(server)
                .privateKey(keyPair.getPrivateKey())
                .publicKey(keyPair.getPublicKey())
                .allocatedIp(allocatedIP)
                .active(true)
                .lastConnectedAt(LocalDateTime.now())
                .build();

        config = configRepository.save(config);

        // Notify OrbMesh server to add peer
        notifyServerAddPeer(server, config);

        return config;
    }

    @Transactional
    public synchronized String allocateIP(Long orbmeshServerId) {
        OrbMeshWireGuardIPPool pool = ipPoolRepository.findByOrbmeshServerId(orbmeshServerId)
                .orElseGet(() -> createIPPool(orbmeshServerId));

        String allocatedIP = pool.getNextAvailableIp();

        String nextIP = WireGuardUtil.incrementIP(allocatedIP);
        pool.setNextAvailableIp(nextIP);
        ipPoolRepository.save(pool);

        log.info("Allocated IP {} for OrbMesh server {}", allocatedIP, orbmeshServerId);
        return allocatedIP;
    }

    private OrbMeshWireGuardIPPool createIPPool(Long orbmeshServerId) {
        return ipPoolRepository.save(OrbMeshWireGuardIPPool.builder()
                .orbmeshServerId(orbmeshServerId)
                .cidr("10.8.0.0/24")
                .gatewayIp("10.8.0.1")
                .nextAvailableIp("10.8.0.2")
                .build());
    }

    private void notifyServerAddPeer(OrbMeshServer server, OrbMeshWireGuardConfig config) {
        try {
            String endpoint = getServerEndpoint(server);
            String url = String.format("https://%s:8443/wireguard/add-peer", endpoint);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + server.getApiKey());

            Map<String, String> request = Map.of(
                    "userUuid", config.getUserUuid(), // ✅ Correct
                    "publicKey", config.getPublicKey(),
                    "allowedIPs", config.getAllocatedIp() + "/32");

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(request, headers);

            ResponseEntity<Void> response = restTemplate.postForEntity(url, entity, Void.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ Notified OrbMesh server {} ({}) to add peer {}",
                        server.getName(), endpoint, config.getUserUuid());
            }

        } catch (Exception e) {
            log.error("❌ Error notifying OrbMesh server to add peer", e);
        }
    }

    @Transactional
    public boolean revokeConfig(User user, Long orbmeshServerId) {
        OrbMeshServer server = serverRepository.findById(orbmeshServerId)
                .orElseThrow(() -> new RuntimeException("OrbMesh server not found: " + orbmeshServerId));

        Optional<OrbMeshWireGuardConfig> config = configRepository
                .findByUserUuidAndServer(user.getUuid(), server);

        if (config.isEmpty()) {
            return false;
        }

        OrbMeshWireGuardConfig wgConfig = config.get();
        wgConfig.setActive(false);
        configRepository.save(wgConfig);

        // Notify server to remove peer
        notifyServerRemovePeer(server, wgConfig);

        log.info("✅ Revoked OrbMesh WireGuard config for user {} on server {}",
                user.getUuid(), server.getName());

        return true;
    }

    private void notifyServerRemovePeer(OrbMeshServer server, OrbMeshWireGuardConfig config) {
        try {
            String endpoint = getServerEndpoint(server);
            String url = String.format("https://%s:8443/wireguard/remove-peer", endpoint);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + server.getApiKey());

            Map<String, String> request = Map.of(
                    "userUuid", config.getUserUuid()); // ✅ Correct

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(request, headers);

            restTemplate.postForEntity(url, entity, Void.class);

            log.info("✅ Notified OrbMesh server {} to remove peer {}",
                    server.getName(), config.getUserUuid());

        } catch (Exception e) {
            log.error("❌ Error notifying server to remove peer", e);
        }
    }

    @Transactional(readOnly = true)
    public List<OrbMeshWireGuardConfig> getUserConfigs(User user) {
        return configRepository.findByUserUuid(user.getUuid());
    }

    /**
     * Sync WireGuard config from mobile app to backend.
     * This is called by the Flutter app after successfully connecting to a server.
     * The app generates keys locally and registers with the Go server,
     * then syncs the config here for admin panel visibility and export functionality.
     *
     * @throws SubscriptionExpiredException if user has no active subscription
     * @throws DeviceLimitExceededException if user has exceeded device limit
     */
    @Transactional
    public OrbMeshWireGuardConfig syncConfig(User user, Long orbmeshServerId, String publicKey,
            String privateKey, String allocatedIp, String serverPublicKey) {
        // Validate subscription and device limit before proceeding
        subscriptionValidationService.validateConnectionAllowed(user);

        OrbMeshServer server = serverRepository.findById(orbmeshServerId)
                .orElseThrow(() -> new RuntimeException("OrbMesh server not found: " + orbmeshServerId));

        // Check if config already exists for this user/server combination
        Optional<OrbMeshWireGuardConfig> existing = configRepository
                .findByUserUuidAndServer(user.getUuid(), server);

        if (existing.isPresent()) {
            OrbMeshWireGuardConfig config = existing.get();
            // Update existing config with new keys (user might have reconnected)
            log.info("🔄 Updating existing WireGuard config for user {} on server {}",
                    user.getUuid(), server.getName());
            config.setPublicKey(publicKey);
            config.setPrivateKey(privateKey);
            config.setAllocatedIp(allocatedIp);
            config.setActive(true);
            config.setLastConnectedAt(LocalDateTime.now());
            return configRepository.save(config);
        }

        // Create new config
        log.info("🔵 Creating new WireGuard config for user {} on server {} (synced from mobile app)",
                user.getUuid(), server.getName());

        OrbMeshWireGuardConfig config = OrbMeshWireGuardConfig.builder()
                .userUuid(user.getUuid())
                .server(server)
                .privateKey(privateKey)
                .publicKey(publicKey)
                .allocatedIp(allocatedIp)
                .active(true)
                .lastConnectedAt(LocalDateTime.now())
                .build();

        return configRepository.save(config);
    }

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