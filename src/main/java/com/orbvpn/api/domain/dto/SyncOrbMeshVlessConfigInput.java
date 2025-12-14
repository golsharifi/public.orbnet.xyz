package com.orbvpn.api.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncOrbMeshVlessConfigInput {
    private Long serverId;
    private String vlessUuid;
    private String flow;
    private String security;
    private String transport;
}
