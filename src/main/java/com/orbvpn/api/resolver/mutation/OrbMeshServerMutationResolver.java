package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.*;
import com.orbvpn.api.service.OrbMeshService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;
import static com.orbvpn.api.domain.enums.RoleName.Constants.ORBMESH_SERVER;

@Slf4j
@Controller
@RequiredArgsConstructor
public class OrbMeshServerMutationResolver {

    private final OrbMeshService orbmeshService;

    @Secured(ADMIN)
    @MutationMapping(name = "registerOrbMeshServer")
    public OrbMeshServerRegistrationResult registerOrbMeshServer(@Argument @Valid OrbMeshServerInput input) {
        log.info("GraphQL mutation: registerOrbMeshServer - name: {}", input.getName());
        return orbmeshService.registerServer(input);
    }

    @Secured({ ADMIN, ORBMESH_SERVER })
    @MutationMapping(name = "updateOrbMeshServer")
    public OrbMeshServerView updateOrbMeshServer(
            @Argument Long id,
            @Argument @Valid OrbMeshServerInput input) {
        log.info("GraphQL mutation: updateOrbMeshServer - id: {}", id);
        return orbmeshService.updateServer(id, input);
    }

    @Secured(ADMIN)
    @MutationMapping(name = "deleteOrbMeshServer")
    public Boolean deleteOrbMeshServer(@Argument Long id) {
        log.info("GraphQL mutation: deleteOrbMeshServer - id: {}", id);
        return orbmeshService.deleteServer(id);
    }

    @Secured(ADMIN)
    @MutationMapping(name = "regenerateOrbMeshServerCredentials")
    public OrbMeshServerRegistrationResult regenerateOrbMeshServerCredentials(@Argument Long id) {
        log.info("GraphQL mutation: regenerateOrbMeshServerCredentials - id: {}", id);
        return orbmeshService.regenerateApiKey(id);
    }

    @Secured({ ADMIN, ORBMESH_SERVER })
    @MutationMapping(name = "updateOrbMeshServerStatus")
    public OrbMeshServerView updateOrbMeshServerStatus(
            @Argument Long serverId,
            @Argument Boolean online) {
        log.info("GraphQL mutation: updateOrbMeshServerStatus - serverId: {}, online: {}", serverId, online);
        return orbmeshService.updateServerStatus(serverId, online);
    }

    @Secured({ ADMIN, ORBMESH_SERVER })
    @MutationMapping(name = "updateOrbMeshServerMetrics")
    public OrbMeshServerView updateOrbMeshServerMetrics(
            @Argument Long serverId,
            @Argument @Valid OrbMeshServerMetricsInput metrics) {
        log.info("GraphQL mutation: updateOrbMeshServerMetrics - serverId: {}", serverId);
        return orbmeshService.updateServerMetrics(serverId, metrics);
    }
}