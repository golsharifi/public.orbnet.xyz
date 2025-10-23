// src/main/java/com/orbvpn/api/resolver/mutation/OrbXWireGuardMutationResolver.java

package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.service.OrbXWireGuardService;
import com.orbvpn.api.service.UserService;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@Slf4j
@RequiredArgsConstructor
public class OrbXWireGuardMutationResolver {

    private final OrbXWireGuardService wireGuardService;
    private final UserService userService;

    @PreAuthorize("isAuthenticated()")
    @MutationMapping
    public Boolean revokeOrbXWireGuardConfig(@Argument Long orbxServerId) {
        User currentUser = userService.getUser();
        log.info("User {} revoking OrbX WireGuard config for server {}",
                currentUser.getEmail(), orbxServerId);

        return wireGuardService.revokeConfig(currentUser, orbxServerId);
    }
}