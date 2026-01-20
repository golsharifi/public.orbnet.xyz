package com.orbvpn.api.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncOrbMeshWireGuardConfigInput {
    private Long serverId;
    private String publicKey;
    private String privateKey;
    private String allocatedIp;
    private String serverPublicKey;
}
