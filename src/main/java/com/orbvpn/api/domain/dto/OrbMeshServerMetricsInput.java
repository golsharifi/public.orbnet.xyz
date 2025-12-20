// src/main/java/com/orbvpn/api/domain/dto/OrbMeshServerMetricsInput.java

package com.orbvpn.api.domain.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrbMeshServerMetricsInput {

    private Integer currentConnections;
    private Double cpuUsage;
    private Double memoryUsage;
    private Integer latencyMs;
}