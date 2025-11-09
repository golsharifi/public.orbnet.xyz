package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.*;
import com.orbvpn.api.service.OrbXService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

@Slf4j
@Controller
@RequiredArgsConstructor
public class OrbXServerMutationResolver {

    private final OrbXService orbxService;

    @Secured(ADMIN)
    @MutationMapping(name = "registerOrbXServer")
    public OrbXServerRegistrationResult registerOrbXServer(@Argument @Valid OrbXServerInput input) {
        log.info("GraphQL mutation: registerOrbXServer - name: {}", input.getName());
        return orbxService.registerServer(input);
    }

    @Secured(ADMIN)
    @MutationMapping(name = "updateOrbXServer")
    public OrbXServerView updateOrbXServer(
            @Argument Long id,
            @Argument @Valid OrbXServerInput input) {
        log.info("GraphQL mutation: updateOrbXServer - id: {}", id);
        return orbxService.updateServer(id, input);
    }

    @Secured(ADMIN)
    @MutationMapping(name = "deleteOrbXServer")
    public Boolean deleteOrbXServer(@Argument Long id) {
        log.info("GraphQL mutation: deleteOrbXServer - id: {}", id);
        return orbxService.deleteServer(id);
    }

    @Secured(ADMIN)
    @MutationMapping(name = "regenerateOrbXServerCredentials")
    public OrbXServerRegistrationResult regenerateOrbXServerCredentials(@Argument Long id) {
        log.info("GraphQL mutation: regenerateOrbXServerCredentials - id: {}", id);
        return orbxService.regenerateApiKey(id);
    }

    // ✅ Allow both ADMIN and ORBX_SERVER role
    @Secured({ "ROLE_ADMIN", "ROLE_ORBX_SERVER" })
    @MutationMapping(name = "updateOrbXServerStatus")
    public OrbXServerView updateOrbXServerStatus(
            @Argument Long serverId,
            @Argument Boolean online) {
        log.info("GraphQL mutation: updateOrbXServerStatus - serverId: {}, online: {}", serverId, online);
        return orbxService.updateServerStatus(serverId, online);
    }

    // ✅ Allow both ADMIN and ORBX_SERVER role
    @Secured({ "ROLE_ADMIN", "ROLE_ORBX_SERVER" })
    @MutationMapping(name = "updateOrbXServerMetrics")
    public OrbXServerView updateOrbXServerMetrics(
            @Argument Long serverId,
            @Argument @Valid OrbXServerMetricsInput metrics) {
        log.info("GraphQL mutation: updateOrbXServerMetrics - serverId: {}", serverId);
        return orbxService.updateServerMetrics(serverId, metrics);
    }
}