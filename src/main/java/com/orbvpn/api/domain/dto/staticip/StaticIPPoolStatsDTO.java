package com.orbvpn.api.domain.dto.staticip;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaticIPPoolStatsDTO {
    private String region;
    private int totalIPs;
    private int allocatedIPs;
    private int availableIPs;
}
