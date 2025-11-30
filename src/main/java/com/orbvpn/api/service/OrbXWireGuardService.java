package com.orbvpn.api.service;

import com.orbvpn.api.domain.entity.OrbXServer;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.OrbXWireGuardConfig;
import com.orbvpn.api.domain.entity.OrbXWireGuardIPPool;
import com.orbvpn.api.exception.DeviceLimitExceededException;
import com.orbvpn.api.exception.SubscriptionExpiredException;
import com.orbvpn.api.repository.OrbXServerRepository;
import com.orbvpn.api.repository.OrbXWireGuardConfigRepository;
import com.orbvpn.api.repository.OrbXWireGuardIPPoolRepository;
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
public class OrbXWireGuardService {

    private final OrbXWireGuardConfigRepository configRepository;
    private final OrbXWireGuardIPPoolRepository ipPoolRepository;
    private final OrbXServerRepository serverRepository;
    private final RestTemplate restTemplate;
    private final OrbXSubscriptionValidationService subscriptionValidationService;

    /**
     * Get all OrbX servers (for app caching)
     */
    public List<OrbXServer> getAllServers() {
        return serverRepository.findByOnlineTrueAndEnabledTrue();
    }

    /**
     * Get or create WireGuard config for specific server.
     *
     * @param user The user requesting the config
     * @param orbxServerId The target server ID
     * @return WireGuard configuration
     * @throws SubscriptionExpiredException if user has no active subscription
     * @throws DeviceLimitExceededException if user has exceeded device limit
     */
    @Transactional
    public OrbXWireGuardConfig getOrCreateConfig(User user, Long orbxServerId) {
        // Validate subscription and device limit before proceeding
        subscriptionValidationService.validateConnectionAllowed(user);

        OrbXServer server = serverRepository.findById(orbxServerId)
                .orElseThrow(() -> new RuntimeException("OrbX server not found: " + orbxServerId));

        // Check if config already exists
        Optional<OrbXWireGuardConfig> existing = configRepository
                .findByUserUuidAndServer(user.getUuid(), server);

        if (existing.isPresent()) {
            OrbXWireGuardConfig config = existing.get();

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
        String allocatedIP = allocateIP(orbxServerId);

        OrbXWireGuardConfig config = OrbXWireGuardConfig.builder()
                .userUuid(user.getUuid())
                .server(server)
                .privateKey(keyPair.getPrivateKey())
                .publicKey(keyPair.getPublicKey())
                .allocatedIp(allocatedIP)
                .active(true)
                .lastConnectedAt(LocalDateTime.now())
                .build();

        config = configRepository.save(config);

        // Notify OrbX server to add peer
        notifyServerAddPeer(server, config);

        return config;
    }

    @Transactional
    public synchronized String allocateIP(Long orbxServerId) {
        OrbXWireGuardIPPool pool = ipPoolRepository.findByOrbxServerId(orbxServerId)
                .orElseGet(() -> createIPPool(orbxServerId));

        String allocatedIP = pool.getNextAvailableIp();

        String nextIP = WireGuardUtil.incrementIP(allocatedIP);
        pool.setNextAvailableIp(nextIP);
        ipPoolRepository.save(pool);

        log.info("Allocated IP {} for OrbX server {}", allocatedIP, orbxServerId);
        return allocatedIP;
    }

    private OrbXWireGuardIPPool createIPPool(Long orbxServerId) {
        return ipPoolRepository.save(OrbXWireGuardIPPool.builder()
                .orbxServerId(orbxServerId)
                .cidr("10.8.0.0/24")
                .gatewayIp("10.8.0.1")
                .nextAvailableIp("10.8.0.2")
                .build());
    }

    private void notifyServerAddPeer(OrbXServer server, OrbXWireGuardConfig config) {
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
                log.info("✅ Notified OrbX server {} ({}) to add peer {}",
                        server.getName(), endpoint, config.getUserUuid());
            }

        } catch (Exception e) {
            log.error("❌ Error notifying OrbX server to add peer", e);
        }
    }

    @Transactional
    public boolean revokeConfig(User user, Long orbxServerId) {
        OrbXServer server = serverRepository.findById(orbxServerId)
                .orElseThrow(() -> new RuntimeException("OrbX server not found: " + orbxServerId));

        Optional<OrbXWireGuardConfig> config = configRepository
                .findByUserUuidAndServer(user.getUuid(), server);

        if (config.isEmpty()) {
            return false;
        }

        OrbXWireGuardConfig wgConfig = config.get();
        wgConfig.setActive(false);
        configRepository.save(wgConfig);

        // Notify server to remove peer
        notifyServerRemovePeer(server, wgConfig);

        log.info("✅ Revoked OrbX WireGuard config for user {} on server {}",
                user.getUuid(), server.getName());

        return true;
    }

    private void notifyServerRemovePeer(OrbXServer server, OrbXWireGuardConfig config) {
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

            log.info("✅ Notified OrbX server {} to remove peer {}",
                    server.getName(), config.getUserUuid());

        } catch (Exception e) {
            log.error("❌ Error notifying server to remove peer", e);
        }
    }

    @Transactional(readOnly = true)
    public List<OrbXWireGuardConfig> getUserConfigs(User user) {
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
    public OrbXWireGuardConfig syncConfig(User user, Long orbxServerId, String publicKey,
            String privateKey, String allocatedIp, String serverPublicKey) {
        // Validate subscription and device limit before proceeding
        subscriptionValidationService.validateConnectionAllowed(user);

        OrbXServer server = serverRepository.findById(orbxServerId)
                .orElseThrow(() -> new RuntimeException("OrbX server not found: " + orbxServerId));

        // Check if config already exists for this user/server combination
        Optional<OrbXWireGuardConfig> existing = configRepository
                .findByUserUuidAndServer(user.getUuid(), server);

        if (existing.isPresent()) {
            OrbXWireGuardConfig config = existing.get();
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

        OrbXWireGuardConfig config = OrbXWireGuardConfig.builder()
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

    private String getServerEndpoint(OrbXServer server) {
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