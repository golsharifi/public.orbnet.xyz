package com.orbvpn.api.domain.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DnsConfigView {
    private boolean enabled;
    private String primaryDns;
    private String secondaryDns;
    private boolean dohEnabled;
    private String dohEndpoint;
    private boolean sniProxyEnabled;
    private int maxWhitelistedIps;
    private int whitelistExpiryDays;
}
