package com.orbvpn.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for Azure Static IP provisioning.
 */
@Data
@Component
@ConfigurationProperties(prefix = "azure.staticip")
public class AzureStaticIPProperties {

    /**
     * Whether Azure dynamic provisioning is enabled.
     * When false, only pre-provisioned IPs from the pool will be used.
     */
    private boolean provisioningEnabled = false;

    /**
     * Azure subscription ID for resource management.
     */
    private String subscriptionId;

    /**
     * Azure tenant ID for authentication.
     */
    private String tenantId;

    /**
     * Azure client ID (application/service principal ID) for authentication.
     */
    private String clientId;

    /**
     * Azure client secret for authentication.
     */
    private String clientSecret;

    /**
     * Resource group name for static IP resources.
     */
    private String resourceGroup;

    /**
     * Default SKU for public IPs (Standard or Basic).
     */
    private String defaultSku = "Standard";

    /**
     * Default allocation method (Static or Dynamic).
     */
    private String allocationMethod = "Static";

    /**
     * IP version (IPv4 or IPv6).
     */
    private String ipVersion = "IPv4";

    /**
     * Prefix for naming Azure public IP resources.
     */
    private String resourceNamePrefix = "orbvpn-staticip";

    /**
     * Cost per month for a static IP (for tracking).
     */
    private String costPerMonth = "3.60";

    /**
     * Maximum IPs that can be provisioned per region.
     */
    private int maxIpsPerRegion = 100;

    /**
     * Timeout in seconds for Azure operations.
     */
    private int operationTimeoutSeconds = 120;

    /**
     * Region-specific configuration overrides.
     * Key: Azure region name (e.g., "eastus", "westeurope")
     * Value: Region-specific settings
     */
    private Map<String, RegionConfig> regions = new HashMap<>();

    @Data
    public static class RegionConfig {
        /**
         * Display name for the region (e.g., "US East", "Europe West").
         */
        private String displayName;

        /**
         * Resource group override for this region (if different from default).
         */
        private String resourceGroup;

        /**
         * Whether this region is enabled for provisioning.
         */
        private boolean enabled = true;

        /**
         * Maximum IPs that can be provisioned in this region.
         */
        private Integer maxIps;
    }
}
