package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.SyncOrbXVlessConfigInput;
import com.orbvpn.api.domain.dto.SyncOrbXVlessConfigResult;
import com.orbvpn.api.domain.entity.OrbXVlessConfig;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.service.OrbXVlessService;
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
public class OrbXVlessMutationResolver {

    private final OrbXVlessService vlessService;
    private final UserService userService;

    /**
     * Sync VLESS config from mobile app to backend.
     * Called by Flutter app after successful VLESS connection.
     */
    @Secured(USER)
    @MutationMapping
    public SyncOrbXVlessConfigResult syncOrbXVlessConfig(@Argument SyncOrbXVlessConfigInput input) {
        try {
            User currentUser = userService.getUser();
            log.info("User {} syncing OrbX VLESS config for server {}",
                    currentUser.getEmail(), input.getServerId());

            OrbXVlessConfig config = vlessService.syncConfig(
                    currentUser,
                    input.getServerId(),
                    input.getVlessUuid(),
                    input.getFlow(),
                    input.getSecurity(),
                    input.getTransport());

            return SyncOrbXVlessConfigResult.builder()
                    .success(true)
                    .configId(config.getId())
                    .vlessUuid(config.getVlessUuid())
                    .message("VLESS config synced successfully")
                    .build();
        } catch (Exception e) {
            log.error("Failed to sync VLESS config", e);
            return SyncOrbXVlessConfigResult.builder()
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
    public Boolean revokeOrbXVlessConfig(@Argument Long orbxServerId) {
        User currentUser = userService.getUser();
        log.info("User {} revoking OrbX VLESS config for server {}",
                currentUser.getEmail(), orbxServerId);

        return vlessService.revokeConfig(currentUser, orbxServerId);
    }
}
