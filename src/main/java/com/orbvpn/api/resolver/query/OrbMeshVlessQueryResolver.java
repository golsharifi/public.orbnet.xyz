package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.entity.OrbMeshVlessConfig;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.service.OrbMeshVlessService;
import com.orbvpn.api.service.UserService;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * GraphQL Query Resolver for VLESS configurations.
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class OrbMeshVlessQueryResolver {

    private final OrbMeshVlessService vlessService;
    private final UserService userService;

    /**
     * Get VLESS config for a specific server.
     * Called when user wants to connect via VLESS.
     */
    @Secured(USER)
    @QueryMapping
    public Map<String, Object> getOrbMeshVlessConfig(@Argument Long orbmeshServerId) {
        User currentUser = userService.getUser();
        log.info("User {} requesting OrbMesh VLESS config for server {}",
                currentUser.getEmail(), orbmeshServerId);

        OrbMeshVlessConfig config = vlessService.getOrCreateConfig(currentUser, orbmeshServerId);

        // Get server endpoint (hostname preferred)
        String endpoint = config.getServer().getHostname() != null && !config.getServer().getHostname().isEmpty()
                ? config.getServer().getHostname()
                : config.getServer().getIpAddress();

        Map<String, Object> response = new HashMap<>();
        response.put("vlessUuid", config.getVlessUuid());
        response.put("flow", config.getFlow());
        response.put("encryption", config.getEncryption());
        response.put("security", config.getSecurity());
        response.put("transport", config.getTransport());
        response.put("serverEndpoint", endpoint);
        response.put("serverPort", 8443); // HTTPS port for mimicry tunnel
        // Reality public key - use dedicated field with fallback to main public key
        response.put("realityPublicKey",
                config.getServer().getRealityPublicKey() != null
                        ? config.getServer().getRealityPublicKey()
                        : config.getServer().getPublicKey());
        // Reality SNI - use server's configured SNI with fallback to default
        response.put("realitySNI",
                config.getServer().getRealitySNI() != null
                        ? config.getServer().getRealitySNI()
                        : "www.microsoft.com");
        response.put("active", config.getActive());
        response.put("createdAt", config.getCreatedAt().toString());

        return response;
    }

    /**
     * Get all VLESS configs for the current user.
     */
    @Secured(USER)
    @QueryMapping
    @Transactional(readOnly = true)
    public List<Map<String, Object>> myOrbMeshVlessConfigs() {
        User currentUser = userService.getUser();
        List<OrbMeshVlessConfig> configs = vlessService.getUserConfigs(currentUser);

        return configs.stream()
                .map(config -> {
                    String endpoint = config.getServer().getHostname() != null
                            && !config.getServer().getHostname().isEmpty()
                                    ? config.getServer().getHostname()
                                    : config.getServer().getIpAddress();

                    Map<String, Object> configMap = new HashMap<>();
                    configMap.put("id", config.getId());
                    configMap.put("vlessUuid", config.getVlessUuid());
                    configMap.put("flow", config.getFlow());
                    configMap.put("encryption", config.getEncryption());
                    configMap.put("security", config.getSecurity());
                    configMap.put("transport", config.getTransport());
                    configMap.put("serverEndpoint", endpoint);
                    configMap.put("serverPort", 8443);
                    configMap.put("serverName", config.getServer().getName());
                    configMap.put("serverRegion", config.getServer().getRegion());
                    configMap.put("serverCountry", config.getServer().getCountry());
                    configMap.put("realityPublicKey",
                            config.getServer().getRealityPublicKey() != null
                                    ? config.getServer().getRealityPublicKey()
                                    : config.getServer().getPublicKey());
                    configMap.put("realitySNI",
                            config.getServer().getRealitySNI() != null
                                    ? config.getServer().getRealitySNI()
                                    : "www.microsoft.com");
                    configMap.put("active", config.getActive());
                    configMap.put("createdAt", config.getCreatedAt().toString());
                    configMap.put("lastConnectedAt",
                            config.getLastConnectedAt() != null ? config.getLastConnectedAt().toString() : null);
                    return configMap;
                })
                .collect(Collectors.toList());
    }

    /**
     * Get active VLESS configs for the current user.
     */
    @Secured(USER)
    @QueryMapping
    @Transactional(readOnly = true)
    public List<Map<String, Object>> myActiveOrbMeshVlessConfigs() {
        User currentUser = userService.getUser();
        List<OrbMeshVlessConfig> configs = vlessService.getActiveUserConfigs(currentUser);

        return configs.stream()
                .map(config -> {
                    String endpoint = config.getServer().getHostname() != null
                            && !config.getServer().getHostname().isEmpty()
                                    ? config.getServer().getHostname()
                                    : config.getServer().getIpAddress();

                    Map<String, Object> configMap = new HashMap<>();
                    configMap.put("id", config.getId());
                    configMap.put("vlessUuid", config.getVlessUuid());
                    configMap.put("serverEndpoint", endpoint);
                    configMap.put("serverPort", 8443);
                    configMap.put("serverName", config.getServer().getName());
                    configMap.put("flow", config.getFlow());
                    configMap.put("security", config.getSecurity());
                    configMap.put("transport", config.getTransport());
                    return configMap;
                })
                .collect(Collectors.toList());
    }
}
