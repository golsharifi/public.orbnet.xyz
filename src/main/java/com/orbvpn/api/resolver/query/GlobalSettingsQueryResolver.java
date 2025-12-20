package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.entity.GlobalSettings;
import com.orbvpn.api.service.GlobalSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class GlobalSettingsQueryResolver {

    private final GlobalSettingsService globalSettingsService;

    /**
     * Get global settings (admin only)
     */
    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Map<String, Object> getGlobalSettings() {
        GlobalSettings settings = globalSettingsService.getSettings();
        log.info("Admin fetching global settings");

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
