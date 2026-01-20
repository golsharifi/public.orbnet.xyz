package com.orbvpn.api.domain.dto.staticip;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegionAvailabilityDTO {
    private String region;
    private int availableCount;
    private String displayName;
    private String countryCode;
    private boolean hasCapacity;
}
