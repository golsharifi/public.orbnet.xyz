package com.orbvpn.api.service.staticip;

import com.azure.core.credential.TokenCredential;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.Region;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.resourcemanager.network.NetworkManager;
import com.azure.resourcemanager.network.models.PublicIpAddress;
import com.azure.resourcemanager.network.models.PublicIPSkuType;
import com.orbvpn.api.config.AzureStaticIPProperties;
import com.orbvpn.api.domain.entity.StaticIPPool;
import com.orbvpn.api.repository.StaticIPPoolRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for dynamically provisioning static IPs from Azure.
 * This service handles creating, managing, and deleting Azure Public IP resources.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@org.springframework.context.annotation.Lazy
public class AzureStaticIPProvisioningService {

    private final AzureStaticIPProperties properties;
    private final StaticIPPoolRepository poolRepository;

    private NetworkManager networkManager;
    private final Map<String, String> regionDisplayNames = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        if (!properties.isProvisioningEnabled()) {
            log.info("Azure Static IP provisioning is disabled");
            return;
        }

        try {
            initializeNetworkManager();
            initializeRegionDisplayNames();
            log.info("Azure Static IP provisioning service initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize Azure Static IP provisioning service: {}", e.getMessage());
        }
    }

    private void initializeNetworkManager() {
        TokenCredential credential;

        if (properties.getClientId() != null && properties.getClientSecret() != null) {
            // Use service principal authentication
            credential = new ClientSecretCredentialBuilder()
                    .tenantId(properties.getTenantId())
                    .clientId(properties.getClientId())
                    .clientSecret(properties.getClientSecret())
                    .build();
        } else {
            // Use default credential chain (managed identity, environment variables, etc.)
            credential = new DefaultAzureCredentialBuilder().build();
        }

        AzureProfile profile = new AzureProfile(
                properties.getTenantId(),
                properties.getSubscriptionId(),
                AzureEnvironment.AZURE
        );

        networkManager = NetworkManager.authenticate(credential, profile);
    }

    private void initializeRegionDisplayNames() {
        // United States
        regionDisplayNames.put("eastus", "US East");
        regionDisplayNames.put("eastus2", "US East 2");
        regionDisplayNames.put("westus", "US West");
        regionDisplayNames.put("westus2", "US West 2");
        regionDisplayNames.put("westus3", "US West 3");
        regionDisplayNames.put("centralus", "US Central");
        regionDisplayNames.put("northcentralus", "US North Central");
        regionDisplayNames.put("southcentralus", "US South Central");
        regionDisplayNames.put("westcentralus", "US West Central");

        // Canada
        regionDisplayNames.put("canadacentral", "Canada Central");
        regionDisplayNames.put("canadaeast", "Canada East");

        // Europe
        regionDisplayNames.put("northeurope", "Europe North (Ireland)");
        regionDisplayNames.put("westeurope", "Europe West (Netherlands)");
        regionDisplayNames.put("uksouth", "UK South");
        regionDisplayNames.put("ukwest", "UK West");
        regionDisplayNames.put("francecentral", "France Central");
        regionDisplayNames.put("francesouth", "France South");
        regionDisplayNames.put("germanywestcentral", "Germany West Central");
        regionDisplayNames.put("germanynorth", "Germany North");
        regionDisplayNames.put("switzerlandnorth", "Switzerland North");
        regionDisplayNames.put("switzerlandwest", "Switzerland West");
        regionDisplayNames.put("norwayeast", "Norway East");
        regionDisplayNames.put("norwaywest", "Norway West");
        regionDisplayNames.put("swedencentral", "Sweden Central");
        regionDisplayNames.put("swedensouth", "Sweden South");
        regionDisplayNames.put("polandcentral", "Poland Central");
        regionDisplayNames.put("italynorth", "Italy North");
        regionDisplayNames.put("spaincentral", "Spain Central");

        // Asia Pacific
        regionDisplayNames.put("eastasia", "Asia East (Hong Kong)");
        regionDisplayNames.put("southeastasia", "Asia Southeast (Singapore)");
        regionDisplayNames.put("japaneast", "Japan East");
        regionDisplayNames.put("japanwest", "Japan West");
        regionDisplayNames.put("koreacentral", "Korea Central");
        regionDisplayNames.put("koreasouth", "Korea South");
        regionDisplayNames.put("centralindia", "India Central");
        regionDisplayNames.put("southindia", "India South");
        regionDisplayNames.put("westindia", "India West");

        // Australia
        regionDisplayNames.put("australiaeast", "Australia East");
        regionDisplayNames.put("australiasoutheast", "Australia Southeast");
        regionDisplayNames.put("australiacentral", "Australia Central");
        regionDisplayNames.put("australiacentral2", "Australia Central 2");

        // Middle East
        regionDisplayNames.put("uaenorth", "UAE North");
        regionDisplayNames.put("uaecentral", "UAE Central");
        regionDisplayNames.put("qatarcentral", "Qatar Central");
        regionDisplayNames.put("israelcentral", "Israel Central");

        // Africa
        regionDisplayNames.put("southafricanorth", "South Africa North");
        regionDisplayNames.put("southafricawest", "South Africa West");

        // South America
        regionDisplayNames.put("brazilsouth", "Brazil South");
        regionDisplayNames.put("brazilsoutheast", "Brazil Southeast");

        // Mexico
        regionDisplayNames.put("mexicocentral", "Mexico Central");

        // New Zealand
        regionDisplayNames.put("newzealandnorth", "New Zealand North");

        // Override with configured display names from properties
        properties.getRegions().forEach((region, config) -> {
            if (config.getDisplayName() != null) {
                regionDisplayNames.put(region, config.getDisplayName());
            }
        });
    }

    /**
     * Check if Azure provisioning is available and enabled.
     */
    public boolean isProvisioningAvailable() {
        return properties.isProvisioningEnabled() && networkManager != null;
    }

    /**
     * Provision a new static IP in the specified Azure region.
     *
     * @param region Azure region name (e.g., "eastus", "westeurope")
     * @param serverId The server ID to associate this IP with
     * @return The provisioned StaticIPPool entry
     */
    @Transactional
    public StaticIPPool provisionStaticIP(String region, Long serverId) {
        if (!isProvisioningAvailable()) {
            throw new IllegalStateException("Azure Static IP provisioning is not enabled or available");
        }

        // Check region configuration
        AzureStaticIPProperties.RegionConfig regionConfig = properties.getRegions().get(region);
        if (regionConfig != null && !regionConfig.isEnabled()) {
            throw new IllegalStateException("Provisioning is disabled for region: " + region);
        }

        // Check region IP limit
        int maxIps = regionConfig != null && regionConfig.getMaxIps() != null
                ? regionConfig.getMaxIps()
                : properties.getMaxIpsPerRegion();
        long currentCount = poolRepository.countByRegion(region);
        if (currentCount >= maxIps) {
            throw new IllegalStateException("Maximum IP limit reached for region: " + region);
        }

        String resourceGroup = regionConfig != null && regionConfig.getResourceGroup() != null
                ? regionConfig.getResourceGroup()
                : properties.getResourceGroup();

        String resourceName = generateResourceName(region);

        log.info("Provisioning new static IP in region {} with name {}", region, resourceName);

        try {
            // Create the public IP in Azure
            PublicIpAddress publicIp = networkManager.publicIpAddresses()
                    .define(resourceName)
                    .withRegion(Region.fromName(region))
                    .withExistingResourceGroup(resourceGroup)
                    .withSku(PublicIPSkuType.STANDARD)
                    .withStaticIP()
                    .create();

            String ipAddress = publicIp.ipAddress();
            String azureResourceId = publicIp.id();

            log.info("Successfully provisioned Azure public IP: {} ({})", ipAddress, azureResourceId);

            // Save to pool
            StaticIPPool poolEntry = StaticIPPool.builder()
                    .publicIp(ipAddress)
                    .region(region)
                    .regionDisplayName(getRegionDisplayName(region))
                    .azureResourceId(azureResourceId)
                    .azureSubscriptionId(properties.getSubscriptionId())
                    .serverId(serverId)
                    .isAllocated(false)
                    .costPerMonth(new BigDecimal(properties.getCostPerMonth()))
                    .createdAt(LocalDateTime.now())
                    .build();

            poolEntry = poolRepository.save(poolEntry);
            log.info("Added new static IP {} to pool with ID {}", ipAddress, poolEntry.getId());

            return poolEntry;

        } catch (Exception e) {
            log.error("Failed to provision static IP in region {}: {}", region, e.getMessage(), e);
            throw new RuntimeException("Failed to provision static IP: " + e.getMessage(), e);
        }
    }

    /**
     * Provision multiple static IPs in a region.
     *
     * @param region Azure region name
     * @param count Number of IPs to provision
     * @param serverId The server ID to associate these IPs with
     * @return List of provisioned StaticIPPool entries
     */
    @Transactional
    public List<StaticIPPool> provisionMultipleStaticIPs(String region, int count, Long serverId) {
        if (count <= 0) {
            throw new IllegalArgumentException("Count must be positive");
        }

        List<StaticIPPool> provisioned = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            try {
                StaticIPPool ip = provisionStaticIP(region, serverId);
                provisioned.add(ip);
            } catch (Exception e) {
                errors.add("IP " + (i + 1) + ": " + e.getMessage());
                log.error("Failed to provision IP {} of {}: {}", i + 1, count, e.getMessage());
            }
        }

        if (!errors.isEmpty() && provisioned.isEmpty()) {
            throw new RuntimeException("Failed to provision any IPs: " + String.join("; ", errors));
        }

        log.info("Successfully provisioned {} of {} requested IPs in region {}",
                provisioned.size(), count, region);

        return provisioned;
    }

    /**
     * Delete a static IP from Azure and remove from pool.
     *
     * @param poolEntry The pool entry to delete
     */
    @Transactional
    public void deprovisionStaticIP(StaticIPPool poolEntry) {
        if (!isProvisioningAvailable()) {
            throw new IllegalStateException("Azure Static IP provisioning is not enabled");
        }

        if (Boolean.TRUE.equals(poolEntry.getIsAllocated())) {
            throw new IllegalStateException("Cannot deprovision an allocated IP. Release it first.");
        }

        String azureResourceId = poolEntry.getAzureResourceId();
        if (azureResourceId == null || azureResourceId.isEmpty()) {
            log.warn("No Azure resource ID for pool entry {}, only removing from database", poolEntry.getId());
            poolRepository.delete(poolEntry);
            return;
        }

        log.info("Deprovisioning static IP {} (Azure: {})", poolEntry.getPublicIp(), azureResourceId);

        try {
            networkManager.publicIpAddresses().deleteById(azureResourceId);
            poolRepository.delete(poolEntry);
            log.info("Successfully deprovisioned and removed static IP {}", poolEntry.getPublicIp());
        } catch (Exception e) {
            log.error("Failed to deprovision static IP {}: {}", poolEntry.getPublicIp(), e.getMessage(), e);
            throw new RuntimeException("Failed to deprovision static IP: " + e.getMessage(), e);
        }
    }

    /**
     * Verify that an Azure public IP still exists and is properly configured.
     *
     * @param poolEntry The pool entry to verify
     * @return true if the IP exists and is valid
     */
    public boolean verifyStaticIP(StaticIPPool poolEntry) {
        if (!isProvisioningAvailable() || poolEntry.getAzureResourceId() == null) {
            return false;
        }

        try {
            PublicIpAddress publicIp = networkManager.publicIpAddresses()
                    .getById(poolEntry.getAzureResourceId());

            if (publicIp == null) {
                log.warn("Azure public IP not found for pool entry {}", poolEntry.getId());
                return false;
            }

            // Verify IP address matches
            if (!publicIp.ipAddress().equals(poolEntry.getPublicIp())) {
                log.warn("IP address mismatch for pool entry {}: DB={}, Azure={}",
                        poolEntry.getId(), poolEntry.getPublicIp(), publicIp.ipAddress());
                return false;
            }

            return true;
        } catch (Exception e) {
            log.error("Failed to verify static IP {}: {}", poolEntry.getPublicIp(), e.getMessage());
            return false;
        }
    }

    /**
     * Get list of available Azure regions for static IP provisioning.
     */
    public List<String> getAvailableRegions() {
        List<String> regions = new ArrayList<>();

        // Add configured regions
        properties.getRegions().forEach((region, config) -> {
            if (config.isEnabled()) {
                regions.add(region);
            }
        });

        // If no regions configured, return common regions
        if (regions.isEmpty()) {
            regions.addAll(List.of(
                    "eastus", "westus2", "westeurope", "northeurope",
                    "southeastasia", "japaneast", "australiaeast"
            ));
        }

        return regions;
    }

    /**
     * Get display name for a region.
     */
    public String getRegionDisplayName(String region) {
        return regionDisplayNames.getOrDefault(region, region);
    }

    /**
     * Find or provision an available static IP in the specified region.
     * First tries to find an existing available IP, then provisions a new one if needed.
     *
     * @param region Azure region
     * @param serverId Server ID for the IP
     * @return Available StaticIPPool entry
     */
    @Transactional
    public StaticIPPool findOrProvisionStaticIP(String region, Long serverId) {
        // First try to find an existing available IP
        Optional<StaticIPPool> existingIp = poolRepository.findFirstAvailableByRegion(region);
        if (existingIp.isPresent()) {
            log.debug("Found existing available IP in region {}", region);
            return existingIp.get();
        }

        // No available IP, provision a new one
        log.info("No available IPs in region {}, provisioning new one", region);
        return provisionStaticIP(region, serverId);
    }

    private String generateResourceName(String region) {
        return String.format("%s-%s-%d",
                properties.getResourceNamePrefix(),
                region,
                System.currentTimeMillis());
    }
}
