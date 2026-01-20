// src/main/java/com/orbvpn/api/resolver/query/OrbMeshWireGuardQueryResolver.java
package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.entity.OrbMeshServer;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.OrbMeshWireGuardConfig;
import com.orbvpn.api.service.OrbMeshWireGuardService;
import com.orbvpn.api.service.UserService;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;

import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@Slf4j
@RequiredArgsConstructor
public class OrbMeshWireGuardQueryResolver {

    private final OrbMeshWireGuardService wireGuardService;
    private final UserService userService;

    /**
     * Get all OrbMesh servers - called once and cached in app
     */
    @Secured(USER)
    @QueryMapping
    public List<Map<String, Object>> getAllOrbMeshServers() {
        User currentUser = userService.getUser();
        log.info("User {} fetching all OrbMesh servers for caching", currentUser.getEmail());

        List<OrbMeshServer> servers = wireGuardService.getAllServers();

        return servers.stream()
                .map(server -> {
                    Map<String, Object> serverMap = new HashMap<>();
                    serverMap.put("id", server.getId());
                    serverMap.put("name", server.getName());
                    serverMap.put("region", server.getRegion());
                    serverMap.put("location", server.getLocation());
                    serverMap.put("country", server.getCountry());

                    // ✅ Return hostname (DNS) as primary endpoint
                    String endpoint = server.getHostname() != null && !server.getHostname().isEmpty()
                            ? server.getHostname()
                            : server.getIpAddress();
                    serverMap.put("endpoint", endpoint);

                    // Include both for debugging/fallback
                    serverMap.put("hostname", server.getHostname());
                    serverMap.put("ipAddress", server.getIpAddress());

                    serverMap.put("port", server.getPort());
                    serverMap.put("wireguardPort", 51820);
                    serverMap.put("wireguardPublicKey",
                            server.getPublicKey() != null ? server.getPublicKey() : "");

                    // VLESS/Reality fields
                    serverMap.put("vlessPort", server.getVlessPort() != null ? server.getVlessPort() : 8443);
                    serverMap.put("realityPublicKey",
                            server.getRealityPublicKey() != null ? server.getRealityPublicKey()
                                    : server.getPublicKey()); // Fallback to main public key
                    serverMap.put("realitySNI",
                            server.getRealitySNI() != null ? server.getRealitySNI() : "www.microsoft.com");

                    // ✅ Protocols - now uses getProtocolsList() from entity
                    List<String> protocols = server.getProtocolsList();
                    serverMap.put("protocols", protocols != null ? protocols : new ArrayList<String>());

                    serverMap.put("maxConnections", server.getMaxConnections());
                    serverMap.put("currentConnections", server.getCurrentConnections());
                    serverMap.put("status", server.getOnline() ? "online" : "offline");
                    serverMap.put("isOnline", server.getOnline());
                    serverMap.put("createdAt",
                            server.getCreatedAt() != null ? server.getCreatedAt().toString() : "");
                    return serverMap;
                })
                .collect(Collectors.toList());
    }

    /**
     * Get WireGuard config - called only when user connects
     */
    @Secured(USER)
    @QueryMapping
    public Map<String, Object> getOrbMeshWireGuardConfig(@Argument Long orbmeshServerId) {
        User currentUser = userService.getUser();
        log.info("User {} requesting OrbMesh WireGuard config for server {}",
                currentUser.getEmail(), orbmeshServerId);

        OrbMeshWireGuardConfig config = wireGuardService.getOrCreateConfig(currentUser, orbmeshServerId);

        // ✅ Return hostname as endpoint (stable DNS name)
        String endpoint = config.getServer().getHostname() != null && !config.getServer().getHostname().isEmpty()
                ? config.getServer().getHostname()
                : config.getServer().getIpAddress();

        Map<String, Object> response = new HashMap<>();
        response.put("privateKey", config.getPrivateKey());
        response.put("publicKey", config.getPublicKey());
        response.put("allocatedIP", config.getAllocatedIp()); // ✅ CORRECT - lowercase 'p' in method name
        response.put("serverEndpoint", endpoint);
        response.put("serverPort", 51820);
        response.put("serverPublicKey", config.getServer().getPublicKey());
        response.put("dns", List.of("1.1.1.1", "1.0.0.1"));
        response.put("mtu", 1420);
        response.put("persistentKeepalive", 25);
        response.put("createdAt", config.getCreatedAt().toString());

        return response;
    }

    @Secured(USER)
    @QueryMapping
    @Transactional(readOnly = true) // ✅ Add this
    public List<Map<String, Object>> myOrbMeshWireGuardConfigs() {
        User currentUser = userService.getUser();
        List<OrbMeshWireGuardConfig> configs = wireGuardService.getUserConfigs(currentUser);

        return configs.stream()
                .map(config -> {
                    String endpoint = config.getServer().getHostname() != null
                            && !config.getServer().getHostname().isEmpty()
                                    ? config.getServer().getHostname()
                                    : config.getServer().getIpAddress();

                    Map<String, Object> configMap = new HashMap<>();
                    configMap.put("privateKey", config.getPrivateKey());
                    configMap.put("publicKey", config.getPublicKey());
                    configMap.put("allocatedIP", config.getAllocatedIp()); // ✅ CORRECT
                    configMap.put("serverEndpoint", endpoint);
                    configMap.put("serverPort", 51820);
                    configMap.put("serverPublicKey", config.getServer().getPublicKey());
                    configMap.put("dns", List.of("1.1.1.1", "1.0.0.1"));
                    configMap.put("mtu", 1420);
                    configMap.put("persistentKeepalive", 25);
                    configMap.put("createdAt", config.getCreatedAt().toString());
                    return configMap;
                })
                .collect(Collectors.toList());
    }
}