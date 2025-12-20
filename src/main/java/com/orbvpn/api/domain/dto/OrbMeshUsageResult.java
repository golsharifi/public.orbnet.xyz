// src/main/java/com/orbvpn/api/domain/dto/OrbMeshUsageResult.java
package com.orbvpn.api.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrbMeshUsageResult {
    private Boolean success;
    private String message;
}