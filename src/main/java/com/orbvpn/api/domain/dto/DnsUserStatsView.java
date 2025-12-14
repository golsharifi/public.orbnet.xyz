package com.orbvpn.api.domain.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DnsUserStatsView {
    private int userId;
    private long totalQueries;
    private long proxiedQueries;
    private int enabledServices;
    private int whitelistedIps;
    private String lastActivity;
}
