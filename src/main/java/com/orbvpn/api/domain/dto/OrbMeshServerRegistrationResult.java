package com.orbvpn.api.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrbMeshServerRegistrationResult {
    private OrbMeshServerView server;
    private String apiKey;
    private String jwtSecret;
}