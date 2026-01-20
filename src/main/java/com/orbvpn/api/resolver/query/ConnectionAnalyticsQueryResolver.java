package com.orbvpn.api.resolver.query;

import com.orbvpn.api.service.ConnectionAnalyticsService;
import com.orbvpn.api.domain.dto.NetworkAnalytics;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import java.time.LocalDateTime;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

@Controller
@RequiredArgsConstructor
public class ConnectionAnalyticsQueryResolver {
    private final ConnectionAnalyticsService connectionAnalyticsService;

    @Secured(ADMIN)
    @QueryMapping
    public NetworkAnalytics networkAnalytics(
            @Argument LocalDateTime from,
            @Argument LocalDateTime to) {
        return connectionAnalyticsService.getNetworkAnalytics(
                from != null ? from : LocalDateTime.now().minusDays(30),
                to != null ? to : LocalDateTime.now());
    }
}