package com.orbvpn.api.service;

import com.orbvpn.api.domain.entity.GlobalSettings;
import com.orbvpn.api.repository.GlobalSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class GlobalSettingsService {

    private final GlobalSettingsRepository globalSettingsRepository;

    /**
     * Get the global settings, creating default settings if none exist.
     */
    @Transactional
    public GlobalSettings getSettings() {
        return globalSettingsRepository.findFirstByOrderByIdAsc()
                .orElseGet(this::createDefaultSettings);
    }

    /**
     * Create default global settings.
     */
    private GlobalSettings createDefaultSettings() {
        log.info("Creating default global settings");
        GlobalSettings settings = GlobalSettings.builder()
                .allowThirdPartyWireGuardClients(false)
                .showWireGuardPrivateKeys(true)
                .maxWireGuardConfigsPerUser(5)
                .build();
        return globalSettingsRepository.save(settings);
    }

    /**
     * Update global settings.
     */
    @Transactional
    public GlobalSettings updateSettings(GlobalSettings updatedSettings) {
        GlobalSettings settings = getSettings();

        if (updatedSettings.getAllowThirdPartyWireGuardClients() != null) {
            settings.setAllowThirdPartyWireGuardClients(updatedSettings.getAllowThirdPartyWireGuardClients());
        }
        if (updatedSettings.getShowWireGuardPrivateKeys() != null) {
            settings.setShowWireGuardPrivateKeys(updatedSettings.getShowWireGuardPrivateKeys());
        }
        if (updatedSettings.getMaxWireGuardConfigsPerUser() != null) {
            settings.setMaxWireGuardConfigsPerUser(updatedSettings.getMaxWireGuardConfigsPerUser());
        }

        log.info("Updated global settings: allowThirdPartyWireGuardClients={}, showWireGuardPrivateKeys={}, maxWireGuardConfigsPerUser={}",
                settings.getAllowThirdPartyWireGuardClients(),
                settings.getShowWireGuardPrivateKeys(),
                settings.getMaxWireGuardConfigsPerUser());

        return globalSettingsRepository.save(settings);
    }

    /**
     * Check if third-party WireGuard clients are allowed.
     */
    @Transactional(readOnly = true)
    public boolean isThirdPartyWireGuardClientsAllowed() {
        return getSettings().getAllowThirdPartyWireGuardClients();
    }

    /**
     * Check if showing WireGuard private keys is allowed.
     */
    @Transactional(readOnly = true)
    public boolean isShowWireGuardPrivateKeysAllowed() {
        return getSettings().getShowWireGuardPrivateKeys();
    }
}
