// src/main/java/com/orbvpn/api/resolver/mutation/OrbXMutationResolver.java
package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.OrbXUsageInput;
import com.orbvpn.api.domain.dto.OrbXUsageResult;
import com.orbvpn.api.service.OrbXUsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class OrbXMutationResolver {

    private final OrbXUsageService usageService;

    @MutationMapping
    public OrbXUsageResult recordOrbXUsage(@Argument OrbXUsageInput input) {
        log.info("Recording OrbX usage for user: {}, session: {}",
                input.getUserId(), input.getSessionId());

        try {
            usageService.recordUsage(input);
            return OrbXUsageResult.builder()
                    .success(true)
                    .message("Usage recorded successfully")
                    .build();
        } catch (Exception e) {
            log.error("Failed to record OrbX usage", e);
            return OrbXUsageResult.builder()
                    .success(false)
                    .message("Failed to record usage: " + e.getMessage())
                    .build();
        }
    }
}