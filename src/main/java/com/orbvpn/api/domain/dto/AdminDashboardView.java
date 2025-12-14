package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class AdminDashboardView {
    private NetworkOverview networkOverview;
    private List<ServerStatus> topServers;
    private List<UserActivity> topUsers;
    private TokenMetrics tokenMetrics;

}