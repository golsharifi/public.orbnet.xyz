package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.SyncOrbMeshVlessConfigInput;
import com.orbvpn.api.domain.dto.SyncOrbMeshVlessConfigResult;
import com.orbvpn.api.domain.entity.OrbMeshVlessConfig;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.service.OrbMeshVlessService;
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
public class OrbMeshVlessMutationResolver {

    private final OrbMeshVlessService vlessService;
    private final UserService userService;

    /**
     * Sync VLESS config from mobile app to backend.
     * Called by Flutter app after successful VLESS connection.
     */
    @Secured(USER)
    @MutationMapping
    public SyncOrbMeshVlessConfigResult syncOrbMeshVlessConfig(@Argument SyncOrbMeshVlessConfigInput input) {
        try {
            User currentUser = userService.getUser();
            log.info("User {} syncing OrbMesh VLESS config for server {}",
                    currentUser.getEmail(), input.getServerId());

            OrbMeshVlessConfig config = vlessService.syncConfig(
                    currentUser,
                    input.getServerId(),
                    input.getVlessUuid(),
                    input.getFlow(),
                    input.getSecurity(),
                    input.getTransport());

            return SyncOrbMeshVlessConfigResult.builder()
                    .success(true)
                    .configId(config.getId())
                    .vlessUuid(config.getVlessUuid())
                    .message("VLESS config synced successfully")
                    .build();
        } catch (Exception e) {
            log.error("Failed to sync VLESS config", e);
            return SyncOrbMeshVlessConfigResult.builder()
                    .success(false)
                    .message("Failed to sync config: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Revoke (deactivate) a VLESS config for a specific server.
     */
    @Secured(USER)
    @MutationMapping
    public Boolean revokeOrbMeshVlessConfig(@Argument Long orbmeshServerId) {
        User currentUser = userService.getUser();
        log.info("User {} revoking OrbMesh VLESS config for server {}",
                currentUser.getEmail(), orbmeshServerId);

        return vlessService.revokeConfig(currentUser, orbmeshServerId);
    }
}
