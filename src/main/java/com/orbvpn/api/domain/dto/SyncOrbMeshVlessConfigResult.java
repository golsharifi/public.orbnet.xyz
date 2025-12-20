package com.orbvpn.api.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncOrbMeshVlessConfigResult {
    private Boolean success;
    private Long configId;
    private String vlessUuid;
    private String message;
}
