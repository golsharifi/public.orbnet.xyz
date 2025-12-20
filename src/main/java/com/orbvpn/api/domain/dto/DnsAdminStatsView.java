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
public class DnsAdminStatsView {
    private int totalUsers;
    private int activeUsers;
    private long totalQueries;
    private long dailyQueries;
    private List<DnsServiceStatView> popularServices;
    private List<DnsRegionStatView> regionDistribution;
    private List<DnsRegionalServerView> serverHealth;
}
