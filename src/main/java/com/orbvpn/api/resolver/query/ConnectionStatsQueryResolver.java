package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.dto.*;
import com.orbvpn.api.service.ConnectionStatsService;
import com.orbvpn.api.service.UserService;
import com.orbvpn.api.service.user.UserContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import java.time.LocalDateTime;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;
import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;

@Controller
@RequiredArgsConstructor
public class ConnectionStatsQueryResolver {
    private final ConnectionStatsService connectionStatsService;
    private final UserContextService userContextService;
    private final UserService userService;

    @Secured(USER)
    @QueryMapping
    public Page<ConnectionStatsView> myConnectionStats(
            @Argument LocalDateTime from,
            @Argument LocalDateTime to,
            @Argument Integer page,
            @Argument Integer size) {
        return connectionStatsService.getUserStats(
                userContextService.getCurrentUser(),
                from != null ? from : LocalDateTime.now().minusDays(30),
                to != null ? to : LocalDateTime.now(),
                page != null ? page : 0,
                size != null ? size : 10);
    }

    @Secured(USER)
    @QueryMapping
    public UserConnectionDashboard myConnectionDashboard() {
        return connectionStatsService.getUserDashboard(
                userContextService.getCurrentUser());
    }

    @Secured(USER)
    @QueryMapping
    public ServerUsageStats myServerUsageStats(
            @Argument Long serverId,
            @Argument LocalDateTime from,
            @Argument LocalDateTime to) {
        return connectionStatsService.getServerUsageStats(
                userContextService.getCurrentUser(),
                serverId,
                from != null ? from : LocalDateTime.now().minusDays(30),
                to != null ? to : LocalDateTime.now());
    }

    @Secured(ADMIN)
    @QueryMapping
    public Page<ConnectionStatsView> userConnectionStats(
            @Argument Integer userId,
            @Argument LocalDateTime from,
            @Argument LocalDateTime to,
            @Argument Integer page,
            @Argument Integer size) {
        return connectionStatsService.getUserStats(
                userService.getUserById(userId),
                from != null ? from : LocalDateTime.now().minusDays(30),
                to != null ? to : LocalDateTime.now(),
                page != null ? page : 0,
                size != null ? size : 10);
    }
}