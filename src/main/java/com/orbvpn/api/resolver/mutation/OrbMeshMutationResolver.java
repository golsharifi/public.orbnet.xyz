// src/main/java/com/orbvpn/api/resolver/mutation/OrbMeshMutationResolver.java
package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.OrbMeshUsageInput;
import com.orbvpn.api.domain.dto.OrbMeshUsageResult;
import com.orbvpn.api.service.OrbMeshUsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class OrbMeshMutationResolver {

    private final OrbMeshUsageService usageService;

    @MutationMapping
    public OrbMeshUsageResult recordOrbMeshUsage(@Argument OrbMeshUsageInput input) {
        log.info("Recording OrbMesh usage for user: {}, session: {}",
                input.getUserId(), input.getSessionId());

        try {
            usageService.recordUsage(input);
            return OrbMeshUsageResult.builder()
                    .success(true)
                    .message("Usage recorded successfully")
                    .build();
        } catch (Exception e) {
            log.error("Failed to record OrbMesh usage", e);
            return OrbMeshUsageResult.builder()
                    .success(false)
                    .message("Failed to record usage: " + e.getMessage())
                    .build();
        }
    }
}