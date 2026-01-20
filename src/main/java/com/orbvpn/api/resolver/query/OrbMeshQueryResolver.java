package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.dto.OrbMeshConfig;
import com.orbvpn.api.domain.dto.OrbMeshServerView;
import com.orbvpn.api.domain.enums.SortType;
import com.orbvpn.api.service.OrbMeshService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;

import java.util.Collections;
import java.util.List;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;
import static com.orbvpn.api.domain.enums.RoleName.Constants.ORBMESH_SERVER;
import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;

@Slf4j
@Controller
@RequiredArgsConstructor
public class OrbMeshQueryResolver {

    private final OrbMeshService orbmeshService;

    @Secured({ USER, ADMIN, ORBMESH_SERVER })
    @QueryMapping
    public List<OrbMeshServerView> orbmeshServers(
            @Argument SortType sortBy,
            @Argument Boolean ascending) {
        log.info("📡 GraphQL query: orbmeshServers - sortBy: {}, ascending: {}", sortBy, ascending);

        try {
            List<OrbMeshServerView> servers = orbmeshService.getOrbMeshServers(sortBy, ascending);
            return servers != null ? servers : Collections.emptyList();
        } catch (Exception e) {
            log.error("❌ Error in orbmeshServers query", e);
            return Collections.emptyList();
        }
    }

    @Secured({ USER, ADMIN })
    @QueryMapping
    public OrbMeshConfig orbmeshConfig(@Argument Long serverId) {
        log.info("📡 GraphQL query: orbmeshConfig - serverId: {}", serverId);
        return orbmeshService.getOrbMeshConfig(serverId);
    }

    @Secured({ USER, ADMIN })
    @QueryMapping(name = "bestOrbMeshServer") // ✅ Add this
    public OrbMeshServerView bestOrbMeshServer() {
        log.info("📡 GraphQL query: bestOrbmeshServer");
        return orbmeshService.getBestOrbMeshServer();
    }

    /**
     * Admin-only query to see ALL servers (including offline/disabled)
     */
    @Secured(ADMIN)
    @QueryMapping
    public List<OrbMeshServerView> allOrbMeshServers() {
        log.info("📡 GraphQL query: allOrbMeshServers (admin)");

        try {
            List<OrbMeshServerView> servers = orbmeshService.getAllServers();

            if (servers == null) {
                log.warn("⚠️ Service returned null, returning empty list");
                return Collections.emptyList();
            }

            log.info("✅ Returning {} servers", servers.size());
            return servers;

        } catch (Exception e) {
            log.error("❌ Error in allOrbMeshServers query", e);
            // Return empty list instead of throwing - prevents GraphQL null error
            return Collections.emptyList();
        }
    }
}