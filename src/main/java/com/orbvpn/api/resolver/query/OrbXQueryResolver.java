package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.dto.OrbXConfig;
import com.orbvpn.api.domain.dto.OrbXServerView;
import com.orbvpn.api.domain.enums.SortType;
import com.orbvpn.api.service.OrbXService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;

import java.util.Collections;
import java.util.List;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;
import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;

@Slf4j
@Controller
@RequiredArgsConstructor
public class OrbXQueryResolver {

    private final OrbXService orbxService;

    @Secured({ USER, ADMIN })
    @QueryMapping
    public List<OrbXServerView> orbxServers(
            @Argument SortType sortBy,
            @Argument Boolean ascending) {
        log.info("📡 GraphQL query: orbxServers - sortBy: {}, ascending: {}", sortBy, ascending);

        try {
            List<OrbXServerView> servers = orbxService.getOrbXServers(sortBy, ascending);
            return servers != null ? servers : Collections.emptyList();
        } catch (Exception e) {
            log.error("❌ Error in orbxServers query", e);
            return Collections.emptyList();
        }
    }

    @Secured({ USER, ADMIN })
    @QueryMapping
    public OrbXConfig orbxConfig(@Argument Long serverId) {
        log.info("📡 GraphQL query: orbxConfig - serverId: {}", serverId);
        return orbxService.getOrbXConfig(serverId);
    }

    @Secured({ USER, ADMIN })
    @QueryMapping(name = "bestOrbXServer") // ✅ Add this
    public OrbXServerView bestOrbXServer() {
        log.info("📡 GraphQL query: bestOrbxServer");
        return orbxService.getBestOrbXServer();
    }

    /**
     * Admin-only query to see ALL servers (including offline/disabled)
     */
    @Secured(ADMIN)
    @QueryMapping
    public List<OrbXServerView> allOrbXServers() {
        log.info("📡 GraphQL query: allOrbXServers (admin)");

        try {
            List<OrbXServerView> servers = orbxService.getAllServers();

            if (servers == null) {
                log.warn("⚠️ Service returned null, returning empty list");
                return Collections.emptyList();
            }

            log.info("✅ Returning {} servers", servers.size());
            return servers;

        } catch (Exception e) {
            log.error("❌ Error in allOrbXServers query", e);
            // Return empty list instead of throwing - prevents GraphQL null error
            return Collections.emptyList();
        }
    }
}