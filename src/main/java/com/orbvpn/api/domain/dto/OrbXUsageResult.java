// src/main/java/com/orbvpn/api/domain/dto/OrbXUsageResult.java
package com.orbvpn.api.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrbXUsageResult {
    private Boolean success;
    private String message;
}