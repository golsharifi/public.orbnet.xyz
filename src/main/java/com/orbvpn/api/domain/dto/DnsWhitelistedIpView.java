package com.orbvpn.api.domain.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DnsWhitelistedIpView {
    private Long id;
    private Integer userId;
    private String ipAddress;
    private String label;
    private String deviceType;
    private boolean active;
    private String lastUsed;
    private String createdAt;
    private String expiresAt;
}
