// src/main/java/com/orbvpn/api/resolver/mutation/OrbXWireGuardMutationResolver.java

package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.SyncOrbXWireGuardConfigInput;
import com.orbvpn.api.domain.dto.SyncOrbXWireGuardConfigResult;
import com.orbvpn.api.domain.entity.OrbXWireGuardConfig;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.service.OrbXWireGuardService;
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
public class OrbXWireGuardMutationResolver {

    private final OrbXWireGuardService wireGuardService;
    private final UserService userService;

    /**
     * Sync WireGuard config from mobile app to backend.
     * Called by Flutter app after successful VPN connection.
     */
    @Secured(USER)
    @MutationMapping
    public SyncOrbXWireGuardConfigResult syncOrbXWireGuardConfig(@Argument SyncOrbXWireGuardConfigInput input) {
        try {
            User currentUser = userService.getUser();
            log.info("User {} syncing OrbX WireGuard config for server {}",
                    currentUser.getEmail(), input.getServerId());

            OrbXWireGuardConfig config = wireGuardService.syncConfig(
                    currentUser,
                    input.getServerId(),
                    input.getPublicKey(),
                    input.getPrivateKey(),
                    input.getAllocatedIp(),
                    input.getServerPublicKey());

            return SyncOrbXWireGuardConfigResult.builder()
                    .success(true)
                    .configId(config.getId())
                    .message("WireGuard config synced successfully")
                    .build();
        } catch (Exception e) {
            log.error("Failed to sync WireGuard config", e);
            return SyncOrbXWireGuardConfigResult.builder()
                    .success(false)
                    .message("Failed to sync config: " + e.getMessage())
                    .build();
        }
    }

    @Secured(USER)
    @MutationMapping
    public Boolean revokeOrbXWireGuardConfig(@Argument Long orbxServerId) {
        User currentUser = userService.getUser();
        log.info("User {} revoking OrbX WireGuard config for server {}",
                currentUser.getEmail(), orbxServerId);

        return wireGuardService.revokeConfig(currentUser, orbxServerId);
    }
}