package com.orbvpn.api.domain.dto.staticip;

import com.orbvpn.api.domain.entity.StaticIPAllocation;
import com.orbvpn.api.domain.entity.StaticIPSubscription;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaticIPDashboardDTO {
    private StaticIPSubscription subscription;
    private List<StaticIPAllocation> allocations;
    private List<StaticIPPlanDTO> availablePlans;
    private List<RegionAvailabilityDTO> availableRegions;
    private List<PortForwardAddonPlanDTO> addonPlans;
}
