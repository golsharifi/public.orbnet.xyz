package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.dto.HistoricalStatsView;
import com.orbvpn.api.domain.entity.ConnectionStatsAggregate.AggregationPeriod;
import com.orbvpn.api.service.HistoricalStatsService;
import com.orbvpn.api.service.UserService;
import com.orbvpn.api.service.user.UserContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;
import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;

@Controller
@RequiredArgsConstructor
public class HistoricalStatsQueryResolver {
    private final HistoricalStatsService historicalStatsService;
    private final UserContextService userContextService;
    private final UserService userService;

    @Secured(USER)
    @QueryMapping
    public HistoricalStatsView myHistoricalStats(
            @Argument AggregationPeriod period,
            @Argument LocalDateTime from,
            @Argument LocalDateTime to) {
        return historicalStatsService.getUserHistoricalStats(
                userContextService.getCurrentUser(),
                period,
                from,
                to);
    }

    @Secured(ADMIN)
    @QueryMapping
    public HistoricalStatsView userHistoricalStats(
            @Argument Integer userId,
            @Argument AggregationPeriod period,
            @Argument LocalDateTime from,
            @Argument LocalDateTime to) {
        return historicalStatsService.getUserHistoricalStats(
                userService.getUserById(userId),
                period,
                from,
                to);
    }

    @Secured(ADMIN)
    @QueryMapping
    public HistoricalStatsView serverHistoricalStats(
            @Argument Long serverId,
            @Argument AggregationPeriod period,
            @Argument LocalDateTime from,
            @Argument LocalDateTime to) {
        return historicalStatsService.getServerHistoricalStats(
                serverId,
                period,
                from,
                to);
    }
}