package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.entity.GlobalSettings;
import com.orbvpn.api.service.GlobalSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class GlobalSettingsMutationResolver {

    private final GlobalSettingsService globalSettingsService;

    /**
     * Update global settings (admin only)
     */
    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Map<String, Object> updateGlobalSettings(@Argument Map<String, Object> input) {
        log.info("Admin updating global settings: {}", input);

        GlobalSettings updates = GlobalSettings.builder().build();

        if (input.containsKey("allowThirdPartyWireGuardClients")) {
            updates.setAllowThirdPartyWireGuardClients((Boolean) input.get("allowThirdPartyWireGuardClients"));
        }
        if (input.containsKey("showWireGuardPrivateKeys")) {
            updates.setShowWireGuardPrivateKeys((Boolean) input.get("showWireGuardPrivateKeys"));
        }
        if (input.containsKey("maxWireGuardConfigsPerUser")) {
            updates.setMaxWireGuardConfigsPerUser((Integer) input.get("maxWireGuardConfigsPerUser"));
        }

        GlobalSettings settings = globalSettingsService.updateSettings(updates);

        Map<String, Object> result = new HashMap<>();
        result.put("id", settings.getId());
        result.put("allowThirdPartyWireGuardClients", settings.getAllowThirdPartyWireGuardClients());
        result.put("showWireGuardPrivateKeys", settings.getShowWireGuardPrivateKeys());
        result.put("maxWireGuardConfigsPerUser", settings.getMaxWireGuardConfigsPerUser());
        result.put("createdAt", settings.getCreatedAt() != null ? settings.getCreatedAt().toString() : null);
        result.put("updatedAt", settings.getUpdatedAt() != null ? settings.getUpdatedAt().toString() : null);

        return result;
    }
}
