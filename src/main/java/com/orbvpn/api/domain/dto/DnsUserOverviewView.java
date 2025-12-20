package com.orbvpn.api.domain.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DnsUserOverviewView {
    private int userId;
    private String username;
    private String email;
    private int enabledServicesCount;
    private int whitelistedIpsCount;
    private long totalQueries;
    private String lastActivity;
    private String subscription;
}
