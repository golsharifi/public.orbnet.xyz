package com.orbvpn.api.domain.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DnsStatsView {
    private long totalQueries;
    private long blockedQueries;
    private long proxiedQueries;
    private long cacheHits;
    private long cacheMisses;
    private double averageLatencyMs;
    private List<DnsServiceStatView> topServices;
    private List<DnsRegionStatView> topRegions;
    private List<DnsHourlyStatView> queriesByHour;
}
