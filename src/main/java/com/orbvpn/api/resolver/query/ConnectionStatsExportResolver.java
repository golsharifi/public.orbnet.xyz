package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.entity.ConnectionStatsAggregate.AggregationPeriod;
import com.orbvpn.api.service.ConnectionStatsExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.util.Base64;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

@Controller
@RequiredArgsConstructor
public class ConnectionStatsExportResolver {
    private final ConnectionStatsExportService exportService;

    @Secured(ADMIN)
    @QueryMapping
    public String exportDetailedStats(
            @Argument LocalDateTime from,
            @Argument LocalDateTime to,
            @Argument Integer userId) {
        byte[] csvData = exportService.exportDetailedStats(from, to, userId);
        return Base64.getEncoder().encodeToString(csvData);
    }

    @Secured(ADMIN)
    @QueryMapping
    public String exportAggregateStats(
            @Argument LocalDateTime from,
            @Argument LocalDateTime to,
            @Argument AggregationPeriod period,
            @Argument Integer userId) {
        byte[] csvData = exportService.exportAggregateStats(from, to, period, userId);
        return Base64.getEncoder().encodeToString(csvData);
    }
}