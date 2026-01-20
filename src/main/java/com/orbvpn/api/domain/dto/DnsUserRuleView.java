package com.orbvpn.api.domain.dto;

import com.orbvpn.api.domain.enums.DnsServiceType;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DnsUserRuleView {
    private Long id;
    private Integer userId;
    private String serviceId;
    private String serviceName;
    private DnsServiceType serviceType;
    private boolean enabled;
    private String preferredRegion;
    private String createdAt;
    private String updatedAt;
}
