package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MetricsTrend {
    private boolean cpuImproving;
    private boolean memoryImproving;
    private boolean networkImproving;
}
