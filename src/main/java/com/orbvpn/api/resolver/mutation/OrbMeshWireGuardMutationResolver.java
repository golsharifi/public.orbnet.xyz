// src/main/java/com/orbvpn/api/resolver/mutation/OrbMeshWireGuardMutationResolver.java

package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.SyncOrbMeshWireGuardConfigInput;
import com.orbvpn.api.domain.dto.SyncOrbMeshWireGuardConfigResult;
import com.orbvpn.api.domain.entity.OrbMeshWireGuardConfig;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.service.OrbMeshWireGuardService;
import com.orbvpn.api.service.UserService;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;

import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@Slf4j
@RequiredArgsConstructor
public class OrbMeshWireGuardMutationResolver {

    private final OrbMeshWireGuardService wireGuardService;
    private final UserService userService;

    /**
     * Sync WireGuard config from mobile app to backend.
     * Called by Flutter app after successful VPN connection.
     */
    @Secured(USER)
    @MutationMapping
    public SyncOrbMeshWireGuardConfigResult syncOrbMeshWireGuardConfig(@Argument SyncOrbMeshWireGuardConfigInput input) {
        try {
            User currentUser = userService.getUser();
            log.info("User {} syncing OrbMesh WireGuard config for server {}",
                    currentUser.getEmail(), input.getServerId());

            OrbMeshWireGuardConfig config = wireGuardService.syncConfig(
                    currentUser,
                    input.getServerId(),
                    input.getPublicKey(),
                    input.getPrivateKey(),
                    input.getAllocatedIp(),
                    input.getServerPublicKey());

            return SyncOrbMeshWireGuardConfigResult.builder()
                    .success(true)
                    .configId(config.getId())
                    .message("WireGuard config synced successfully")
                    .build();
        } catch (Exception e) {
            log.error("Failed to sync WireGuard config", e);
            return SyncOrbMeshWireGuardConfigResult.builder()
                    .success(false)
                    .message("Failed to sync config: " + e.getMessage())
                    .build();
        }
    }

    @Secured(USER)
    @MutationMapping
    public Boolean revokeOrbMeshWireGuardConfig(@Argument Long orbmeshServerId) {
        User currentUser = userService.getUser();
        log.info("User {} revoking OrbMesh WireGuard config for server {}",
                currentUser.getEmail(), orbmeshServerId);

        return wireGuardService.revokeConfig(currentUser, orbmeshServerId);
    }
}