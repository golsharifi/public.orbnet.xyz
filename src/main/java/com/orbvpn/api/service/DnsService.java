package com.orbvpn.api.service;

import com.orbvpn.api.domain.dto.*;
import com.orbvpn.api.domain.entity.DnsConfig;
import com.orbvpn.api.domain.entity.DnsQueryLog;
import com.orbvpn.api.domain.entity.DnsUserRule;
import com.orbvpn.api.domain.entity.DnsWhitelistedIp;
import com.orbvpn.api.domain.entity.OrbMeshServer;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.enums.DnsServiceType;
import com.orbvpn.api.exception.BadRequestException;
import com.orbvpn.api.exception.NotFoundException;
import com.orbvpn.api.repository.*;
import com.orbvpn.api.service.subscription.UserSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.yaml.snakeyaml.Yaml;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DnsService {

    private final DnsUserRuleRepository userRuleRepository;
    private final DnsWhitelistedIpRepository whitelistedIpRepository;
    private final DnsQueryLogRepository queryLogRepository;
    private final DnsConfigRepository configRepository;
    private final UserRepository userRepository;
    private final GeolocationService geolocationService;
    private final UserSubscriptionService userSubscriptionService;
    private final OrbMeshServerRepository orbMeshServerRepository;
    private final RestTemplate restTemplate;

    @Value("${dns.services.streaming-config:streaming_services.yaml}")
    private String streamingConfigPath;

    @Value("${dns.services.regional-config:regional_services.yaml}")
    private String regionalConfigPath;

    @Value("${orbmesh.dns.sync-url:}")
    private String orbmeshDnsSyncUrl;

    @Value("${orbmesh.api-key:}")
    private String orbmeshApiKey;

    private List<DnsServiceCategoryView> streamingCategories;
    private List<DnsRegionalCategoryView> regionalCategories;
    private Map<String, DnsServiceView> streamingServicesMap;
    private Map<String, DnsRegionalServiceView> regionalServicesMap;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @PostConstruct
    public void init() {
        log.info("DNS Service initializing - loading service catalogs...");
        loadStreamingServices();
        loadRegionalServices();
        log.info("DNS Service initialized - {} streaming categories, {} regional categories",
            streamingCategories != null ? streamingCategories.size() : 0,
            regionalCategories != null ? regionalCategories.size() : 0);
    }

    // ========================================================================
    // SERVICE CATALOG METHODS
    // ========================================================================

    @SuppressWarnings("unchecked")
    private void loadStreamingServices() {
        String resourcePath = "dns/" + streamingConfigPath;
        log.info("Loading streaming services from: {}", resourcePath);
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            if (!resource.exists()) {
                log.error("Streaming services resource NOT FOUND: {}", resourcePath);
                streamingCategories = new ArrayList<>();
                streamingServicesMap = new HashMap<>();
                return;
            }

            Yaml yaml = new Yaml();
            InputStream inputStream = resource.getInputStream();
            Map<String, Object> data = yaml.load(inputStream);

            if (data == null) {
                log.error("Streaming services YAML data is null");
                streamingCategories = new ArrayList<>();
                streamingServicesMap = new HashMap<>();
                return;
            }

            streamingCategories = new ArrayList<>();
            streamingServicesMap = new HashMap<>();

            List<Map<String, Object>> categories = (List<Map<String, Object>>) data.get("categories");
            if (categories != null) {
                log.info("Found {} categories in streaming YAML", categories.size());
                for (Map<String, Object> category : categories) {
                    DnsServiceCategoryView categoryView = buildStreamingCategory(category);
                    streamingCategories.add(categoryView);
                }
            } else {
                log.warn("No 'categories' key found in streaming YAML");
            }

            log.info("Loaded {} streaming service categories with {} total services",
                streamingCategories.size(), streamingServicesMap.size());
        } catch (Exception e) {
            log.error("Failed to load streaming services config from {}: {}", resourcePath, e.getMessage(), e);
            streamingCategories = new ArrayList<>();
            streamingServicesMap = new HashMap<>();
        }
    }

    @SuppressWarnings("unchecked")
    private DnsServiceCategoryView buildStreamingCategory(Map<String, Object> category) {
        String categoryId = (String) category.get("id");
        String categoryName = (String) category.get("name");

        List<DnsServiceView> services = new ArrayList<>();
        List<Map<String, Object>> serviceList = (List<Map<String, Object>>) category.get("services");

        if (serviceList != null) {
            for (Map<String, Object> service : serviceList) {
                DnsServiceView serviceView = DnsServiceView.builder()
                    .id((String) service.get("id"))
                    .name((String) service.get("name"))
                    .icon((String) service.get("icon"))
                    .description((String) service.get("description"))
                    .category(categoryId)
                    .categoryName(categoryName)
                    .domains((List<String>) service.getOrDefault("domains", new ArrayList<>()))
                    .regions((List<String>) service.getOrDefault("regions", new ArrayList<>()))
                    .enabled(true)
                    .popular(Boolean.TRUE.equals(service.get("popular")))
                    .build();

                services.add(serviceView);
                streamingServicesMap.put(serviceView.getId(), serviceView);
            }
        }

        return DnsServiceCategoryView.builder()
            .id(categoryId)
            .name(categoryName)
            .icon((String) category.get("icon"))
            .description((String) category.get("description"))
            .services(services)
            .build();
    }

    @SuppressWarnings("unchecked")
    private void loadRegionalServices() {
        String resourcePath = "dns/" + regionalConfigPath;
        log.info("Loading regional services from: {}", resourcePath);
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            if (!resource.exists()) {
                log.error("Regional services resource NOT FOUND: {}", resourcePath);
                regionalCategories = new ArrayList<>();
                regionalServicesMap = new HashMap<>();
                return;
            }

            Yaml yaml = new Yaml();
            InputStream inputStream = resource.getInputStream();
            Map<String, Object> data = yaml.load(inputStream);

            if (data == null) {
                log.error("Regional services YAML data is null");
                regionalCategories = new ArrayList<>();
                regionalServicesMap = new HashMap<>();
                return;
            }

            regionalCategories = new ArrayList<>();
            regionalServicesMap = new HashMap<>();

            List<Map<String, Object>> categories = (List<Map<String, Object>>) data.get("categories");
            if (categories != null) {
                log.info("Found {} categories in regional YAML", categories.size());
                for (Map<String, Object> category : categories) {
                    DnsRegionalCategoryView categoryView = buildRegionalCategory(category);
                    regionalCategories.add(categoryView);
                }
            } else {
                log.warn("No 'categories' key found in regional YAML");
            }

            log.info("Loaded {} Regional service categories with {} total services",
                regionalCategories.size(), regionalServicesMap.size());
        } catch (Exception e) {
            log.error("Failed to load Regional services config from {}: {}", resourcePath, e.getMessage(), e);
            regionalCategories = new ArrayList<>();
            regionalServicesMap = new HashMap<>();
        }
    }

    @SuppressWarnings("unchecked")
    private DnsRegionalCategoryView buildRegionalCategory(Map<String, Object> category) {
        String categoryId = (String) category.get("id");
        String categoryName = (String) category.get("name");

        List<DnsRegionalServiceView> services = new ArrayList<>();
        List<Map<String, Object>> serviceList = (List<Map<String, Object>>) category.get("services");

        if (serviceList != null) {
            for (Map<String, Object> service : serviceList) {
                DnsRegionalServiceView serviceView = DnsRegionalServiceView.builder()
                    .id((String) service.get("id"))
                    .name((String) service.get("name"))
                    .icon((String) service.get("icon"))
                    .description((String) service.get("description"))
                    .category(categoryId)
                    .categoryName(categoryName)
                    .domains((List<String>) service.getOrDefault("domains", new ArrayList<>()))
                    .requiresIranIp(Boolean.TRUE.equals(service.get("requires_iran_ip")))
                    .enabled(true)
                    .build();

                services.add(serviceView);
                regionalServicesMap.put(serviceView.getId(), serviceView);
            }
        }

        return DnsRegionalCategoryView.builder()
            .id(categoryId)
            .name(categoryName)
            .icon((String) category.get("icon"))
            .description((String) category.get("description"))
            .services(services)
            .build();
    }

    public List<DnsServiceCategoryView> getStreamingServices() {
        log.debug("getStreamingServices called - returning {} categories",
            streamingCategories != null ? streamingCategories.size() : 0);
        return streamingCategories;
    }

    public List<DnsServiceView> getStreamingServicesByRegion(String region) {
        return streamingServicesMap.values().stream()
            .filter(s -> s.getRegions().contains(region))
            .collect(Collectors.toList());
    }

    public List<DnsServiceView> getPopularStreamingServices() {
        return streamingServicesMap.values().stream()
            .filter(DnsServiceView::isPopular)
            .collect(Collectors.toList());
    }

    public List<DnsServiceView> searchStreamingServices(String query) {
        String lowerQuery = query.toLowerCase();
        return streamingServicesMap.values().stream()
            .filter(s -> s.getName().toLowerCase().contains(lowerQuery) ||
                         s.getDescription() != null && s.getDescription().toLowerCase().contains(lowerQuery))
            .collect(Collectors.toList());
    }

    public List<DnsRegionalCategoryView> getRegionalServices() {
        return regionalCategories;
    }

    public List<DnsRegionalServiceView> getRegionalServicesByCategory(String categoryId) {
        return regionalCategories.stream()
            .filter(c -> c.getId().equals(categoryId))
            .flatMap(c -> c.getServices().stream())
            .collect(Collectors.toList());
    }

    // ========================================================================
    // USER RULES METHODS
    // ========================================================================

    public List<DnsUserRuleView> getUserRules(User user) {
        return userRuleRepository.findByUser(user).stream()
            .map(this::toUserRuleView)
            .collect(Collectors.toList());
    }

    public DnsUserRuleView getUserRule(User user, String serviceId, DnsServiceType serviceType) {
        return userRuleRepository.findByUserAndServiceIdAndServiceType(user, serviceId, serviceType)
            .map(this::toUserRuleView)
            .orElse(null);
    }

    @Transactional
    public DnsUserRuleView setUserRule(User user, DnsServiceRuleInput input) {
        String serviceName = getServiceName(input.getServiceId(), input.getServiceType());

        DnsUserRule rule = userRuleRepository
            .findByUserAndServiceIdAndServiceType(user, input.getServiceId(), input.getServiceType())
            .orElse(new DnsUserRule());

        rule.setUser(user);
        rule.setServiceId(input.getServiceId());
        rule.setServiceName(serviceName);
        rule.setServiceType(input.getServiceType());
        rule.setEnabled(input.isEnabled());
        rule.setPreferredRegion(input.getPreferredRegion());

        DnsUserRule saved = userRuleRepository.save(rule);
        log.info("Set DNS rule for user {} service {} enabled={}", user.getId(), input.getServiceId(), input.isEnabled());

        // Sync to Go server
        syncUserRulesToServer(user.getId());

        return toUserRuleView(saved);
    }

    @Transactional
    public boolean removeUserRule(User user, String serviceId, DnsServiceType serviceType) {
        userRuleRepository.deleteByUserAndServiceIdAndServiceType(user, serviceId, serviceType);
        log.info("Removed DNS rule for user {} service {}", user.getId(), serviceId);
        syncUserRulesToServer(user.getId());
        return true;
    }

    @Transactional
    public int enableAllServices(User user, DnsServiceType serviceType) {
        // Get all services of this type and create/update rules for each
        int count = 0;
        if (serviceType == DnsServiceType.STREAMING) {
            for (DnsServiceView service : streamingServicesMap.values()) {
                DnsUserRule rule = userRuleRepository
                    .findByUserAndServiceIdAndServiceType(user, service.getId(), serviceType)
                    .orElse(new DnsUserRule());
                rule.setUser(user);
                rule.setServiceId(service.getId());
                rule.setServiceName(service.getName());
                rule.setServiceType(serviceType);
                rule.setEnabled(true);
                userRuleRepository.save(rule);
                count++;
            }
        } else {
            for (DnsRegionalServiceView service : regionalServicesMap.values()) {
                DnsUserRule rule = userRuleRepository
                    .findByUserAndServiceIdAndServiceType(user, service.getId(), serviceType)
                    .orElse(new DnsUserRule());
                rule.setUser(user);
                rule.setServiceId(service.getId());
                rule.setServiceName(service.getName());
                rule.setServiceType(serviceType);
                rule.setEnabled(true);
                userRuleRepository.save(rule);
                count++;
            }
        }
        log.info("Enabled {} {} services for user {}", count, serviceType, user.getId());
        syncUserRulesToServer(user.getId());
        return count;
    }

    @Transactional
    public int disableAllServices(User user, DnsServiceType serviceType) {
        // Get all services of this type and create/update rules for each
        int count = 0;
        if (serviceType == DnsServiceType.STREAMING) {
            for (DnsServiceView service : streamingServicesMap.values()) {
                DnsUserRule rule = userRuleRepository
                    .findByUserAndServiceIdAndServiceType(user, service.getId(), serviceType)
                    .orElse(new DnsUserRule());
                rule.setUser(user);
                rule.setServiceId(service.getId());
                rule.setServiceName(service.getName());
                rule.setServiceType(serviceType);
                rule.setEnabled(false);
                userRuleRepository.save(rule);
                count++;
            }
        } else {
            for (DnsRegionalServiceView service : regionalServicesMap.values()) {
                DnsUserRule rule = userRuleRepository
                    .findByUserAndServiceIdAndServiceType(user, service.getId(), serviceType)
                    .orElse(new DnsUserRule());
                rule.setUser(user);
                rule.setServiceId(service.getId());
                rule.setServiceName(service.getName());
                rule.setServiceType(serviceType);
                rule.setEnabled(false);
                userRuleRepository.save(rule);
                count++;
            }
        }
        log.info("Disabled {} {} services for user {}", count, serviceType, user.getId());
        syncUserRulesToServer(user.getId());
        return count;
    }

    @Transactional
    public boolean resetUserRulesToDefault(User user) {
        userRuleRepository.deleteAllByUser(user);
        syncUserRulesToServer(user.getId());
        return true;
    }

    private String getServiceName(String serviceId, DnsServiceType serviceType) {
        if (serviceType == DnsServiceType.STREAMING) {
            DnsServiceView service = streamingServicesMap.get(serviceId);
            return service != null ? service.getName() : serviceId;
        } else {
            DnsRegionalServiceView service = regionalServicesMap.get(serviceId);
            return service != null ? service.getName() : serviceId;
        }
    }

    // ========================================================================
    // IP WHITELIST METHODS
    // ========================================================================

    public List<DnsWhitelistedIpView> getWhitelistedIps(User user) {
        return whitelistedIpRepository.findByUser(user).stream()
            .map(this::toWhitelistedIpView)
            .collect(Collectors.toList());
    }

    @Transactional
    public DnsWhitelistedIpView whitelistIp(User user, DnsWhitelistIpInput input) {
        DnsConfig config = getOrCreateConfig();

        // Get user's subscription to determine device/IP whitelist limit
        var subscription = userSubscriptionService.getUserSubscription(user);
        int maxWhitelistedIps = subscription != null ? subscription.getMultiLoginCount() : 1;

        // Check limit - use user's multiLoginCount (same as device limit)
        int currentCount = whitelistedIpRepository.countActiveByUserId(user.getId());
        if (currentCount >= maxWhitelistedIps) {
            throw new BadRequestException("Maximum whitelisted IPs limit reached (" + maxWhitelistedIps + "). Your subscription allows " + maxWhitelistedIps + " devices/IPs.");
        }

        // Check if already whitelisted
        Optional<DnsWhitelistedIp> existing = whitelistedIpRepository.findByUserAndIpAddress(user, input.getIpAddress());
        if (existing.isPresent()) {
            DnsWhitelistedIp ip = existing.get();
            ip.setActive(true);
            ip.setLabel(input.getLabel());
            ip.setDeviceType(input.getDeviceType());
            if (input.getExpiryDays() != null && input.getExpiryDays() > 0) {
                ip.setExpiresAt(LocalDateTime.now().plusDays(input.getExpiryDays()));
            } else {
                ip.setExpiresAt(LocalDateTime.now().plusDays(config.getWhitelistExpiryDays()));
            }
            return toWhitelistedIpView(whitelistedIpRepository.save(ip));
        }

        DnsWhitelistedIp whitelistedIp = new DnsWhitelistedIp(user, input.getIpAddress(), input.getLabel(), input.getDeviceType());
        if (input.getExpiryDays() != null && input.getExpiryDays() > 0) {
            whitelistedIp.setExpiresAt(LocalDateTime.now().plusDays(input.getExpiryDays()));
        } else {
            whitelistedIp.setExpiresAt(LocalDateTime.now().plusDays(config.getWhitelistExpiryDays()));
        }

        DnsWhitelistedIp saved = whitelistedIpRepository.save(whitelistedIp);
        log.info("Whitelisted IP {} for user {}", input.getIpAddress(), user.getId());

        // Sync to Go server
        syncWhitelistToServer(user.getId());

        return toWhitelistedIpView(saved);
    }

    @Transactional
    public DnsWhitelistedIpView whitelistCurrentIp(User user, String label, String deviceType, String currentIp) {
        if (currentIp == null || currentIp.isEmpty()) {
            throw new BadRequestException("Could not determine current IP address");
        }

        DnsWhitelistIpInput input = new DnsWhitelistIpInput();
        input.setIpAddress(currentIp);
        input.setLabel(label != null ? label : "Current Device");
        input.setDeviceType(deviceType);

        return whitelistIp(user, input);
    }

    @Transactional
    public DnsWhitelistedIpView updateWhitelistedIp(User user, Long id, String label, Boolean active) {
        DnsWhitelistedIp ip = whitelistedIpRepository.findByIdAndUser(id, user)
            .orElseThrow(() -> new NotFoundException("Whitelisted IP not found"));

        if (label != null) {
            ip.setLabel(label);
        }
        if (active != null) {
            ip.setActive(active);
        }

        DnsWhitelistedIp saved = whitelistedIpRepository.save(ip);
        syncWhitelistToServer(user.getId());
        return toWhitelistedIpView(saved);
    }

    @Transactional
    public boolean removeWhitelistedIp(User user, Long id) {
        DnsWhitelistedIp ip = whitelistedIpRepository.findByIdAndUser(id, user)
            .orElseThrow(() -> new NotFoundException("Whitelisted IP not found"));

        whitelistedIpRepository.delete(ip);
        log.info("Removed whitelisted IP {} for user {}", ip.getIpAddress(), user.getId());
        syncWhitelistToServer(user.getId());
        return true;
    }

    // ========================================================================
    // CONFIG METHODS
    // ========================================================================

    public DnsConfigView getConfig() {
        DnsConfig config = getOrCreateConfig();
        return toConfigView(config);
    }

    /**
     * Get DNS config with user-specific whitelist limit based on their subscription.
     * The maxWhitelistedIps is set to match the user's multiLoginCount (device limit).
     */
    public DnsConfigView getConfigForUser(User user) {
        DnsConfig config = getOrCreateConfig();
        var subscription = userSubscriptionService.getUserSubscription(user);
        int userWhitelistLimit = subscription != null ? subscription.getMultiLoginCount() : 1;

        return DnsConfigView.builder()
            .enabled(config.isEnabled())
            .primaryDns(config.getPrimaryDns())
            .secondaryDns(config.getSecondaryDns())
            .dohEnabled(config.isDohEnabled())
            .dohEndpoint(config.getDohEndpoint())
            .sniProxyEnabled(config.isSniProxyEnabled())
            .maxWhitelistedIps(userWhitelistLimit)  // Use user's subscription limit
            .whitelistExpiryDays(config.getWhitelistExpiryDays())
            .build();
    }

    @Transactional
    public DnsConfigView updateConfig(Boolean enabled, Boolean dohEnabled, Boolean sniProxyEnabled,
                                       Integer maxWhitelistedIps, Integer whitelistExpiryDays) {
        DnsConfig config = getOrCreateConfig();

        if (enabled != null) config.setEnabled(enabled);
        if (dohEnabled != null) config.setDohEnabled(dohEnabled);
        if (sniProxyEnabled != null) config.setSniProxyEnabled(sniProxyEnabled);
        if (maxWhitelistedIps != null) config.setMaxWhitelistedIps(maxWhitelistedIps);
        if (whitelistExpiryDays != null) config.setWhitelistExpiryDays(whitelistExpiryDays);

        DnsConfig saved = configRepository.save(config);
        log.info("Updated DNS config: enabled={}, doh={}, sni={}",
            config.isEnabled(), config.isDohEnabled(), config.isSniProxyEnabled());

        return toConfigView(saved);
    }

    private DnsConfig getOrCreateConfig() {
        return configRepository.findGlobalConfig()
            .orElseGet(() -> configRepository.save(DnsConfig.getDefault()));
    }

    // ========================================================================
    // STATS METHODS
    // ========================================================================

    public DnsUserStatsView getUserStats(User user) {
        int userId = user.getId();

        long totalQueries = queryLogRepository.countByUserId(userId);
        long proxiedQueries = queryLogRepository.countProxiedByUserId(userId);
        int enabledServices = userRuleRepository.countEnabledByUserId(userId);
        int whitelistedIps = whitelistedIpRepository.countActiveByUserId(userId);
        LocalDateTime lastActivity = queryLogRepository.findLastActivityByUserId(userId);

        return DnsUserStatsView.builder()
            .userId(userId)
            .totalQueries(totalQueries)
            .proxiedQueries(proxiedQueries)
            .enabledServices(enabledServices)
            .whitelistedIps(whitelistedIps)
            .lastActivity(lastActivity != null ? lastActivity.format(DATE_FORMATTER) : null)
            .build();
    }

    public DnsAdminStatsView getAdminStats() {
        int totalUsers = userRuleRepository.countDistinctUsersWithEnabledRules();
        int activeUsers = whitelistedIpRepository.countDistinctUsersWithWhitelistedIps();
        long totalQueries = queryLogRepository.countAll();
        long dailyQueries = queryLogRepository.countSince(LocalDateTime.now().minusDays(1));

        List<Object[]> serviceStats = queryLogRepository.findServiceStats(PageRequest.of(0, 10));
        List<DnsServiceStatView> popularServices = serviceStats.stream()
            .map(row -> DnsServiceStatView.builder()
                .serviceId((String) row[0])
                .serviceName(getServiceName((String) row[0], DnsServiceType.STREAMING))
                .queryCount((Long) row[1])
                .build())
            .collect(Collectors.toList());

        List<Object[]> regionStats = queryLogRepository.findRegionStats();
        List<DnsRegionStatView> regionDistribution = regionStats.stream()
            .map(row -> DnsRegionStatView.builder()
                .region((String) row[0])
                .queryCount((Long) row[1])
                .build())
            .collect(Collectors.toList());

        return DnsAdminStatsView.builder()
            .totalUsers(totalUsers)
            .activeUsers(activeUsers)
            .totalQueries(totalQueries)
            .dailyQueries(dailyQueries)
            .popularServices(popularServices)
            .regionDistribution(regionDistribution)
            .serverHealth(getRegionalServers())
            .build();
    }

    // ========================================================================
    // REGIONAL SERVERS
    // ========================================================================

    /**
     * Get regional servers from real OrbMeshServer data.
     * These are the actual MESH servers that handle DNS proxying for different regions.
     */
    public List<DnsRegionalServerView> getRegionalServers() {
        // Get all enabled servers from the database (regardless of online status)
        // We show all enabled servers and let the UI display their health status
        List<OrbMeshServer> servers = orbMeshServerRepository.findByEnabledTrue();

        if (servers.isEmpty()) {
            log.warn("No enabled servers found for DNS regional servers");
            // Return empty list if no servers available
            return Collections.emptyList();
        }

        // Map country codes to regional display names
        Map<String, String> countryNames = new HashMap<>();
        countryNames.put("US", "United States");
        countryNames.put("UK", "United Kingdom");
        countryNames.put("GB", "United Kingdom");
        countryNames.put("JP", "Japan");
        countryNames.put("DE", "Germany");
        countryNames.put("AU", "Australia");
        countryNames.put("CA", "Canada");
        countryNames.put("FR", "France");
        countryNames.put("IT", "Italy");
        countryNames.put("BR", "Brazil");
        countryNames.put("KR", "South Korea");
        countryNames.put("IR", "Iran");
        countryNames.put("NL", "Netherlands");
        countryNames.put("SG", "Singapore");

        return servers.stream()
            .map(server -> {
                String countryCode = server.getCountryCode() != null ?
                    server.getCountryCode().toUpperCase() : "US";
                String countryName = countryNames.getOrDefault(countryCode, server.getCountry());

                // For DNS servers, we consider them healthy if they are marked as online and enabled.
                // The `online` flag is set by the OrbMESH heartbeat system, which is the source of truth.
                // We only override this if the lastHeartbeat is VERY stale (30+ minutes), indicating
                // the heartbeat system itself may have failed.
                boolean healthy = server.getOnline() && server.getEnabled();

                // Note: We trust the online flag set by the heartbeat system.
                // Only mark unhealthy if heartbeat is extremely stale (30+ minutes)
                // which would indicate something is very wrong with the server.
                if (healthy && server.getLastHeartbeat() != null) {
                    boolean veryStale = server.getLastHeartbeat().isBefore(LocalDateTime.now().minusMinutes(30));
                    if (veryStale) {
                        log.warn("Server {} has very stale heartbeat: {}", server.getName(), server.getLastHeartbeat());
                        healthy = false;
                    }
                }

                return DnsRegionalServerView.builder()
                    .code(countryCode)
                    .name(countryName != null ? countryName : server.getName())
                    .location(server.getLocation())
                    .ipv4(server.getIpAddress())
                    .healthy(healthy)
                    .latency(server.getLatencyMs())
                    .priority(1) // All servers have same priority for now
                    .build();
            })
            .collect(Collectors.toList());
    }

    // ========================================================================
    // ADMIN METHODS
    // ========================================================================

    public List<DnsUserOverviewView> getAdminUserOverview(int limit, int offset) {
        // Get users who have DNS rules or whitelisted IPs
        List<Object[]> userSummaries = userRuleRepository.findUsersWithDnsActivity(
            PageRequest.of(offset / limit, limit));

        return userSummaries.stream()
            .map(row -> {
                int userId = ((Number) row[0]).intValue();
                String email = (String) row[1];
                String username = (String) row[2];
                int enabledCount = ((Number) row[3]).intValue();

                // Get additional data
                int whitelistCount = whitelistedIpRepository.countActiveByUserId(userId);
                long totalQueries = queryLogRepository.countByUserId(userId);

                // Get subscription info
                String subscriptionName = null;
                try {
                    User user = userRepository.findById(userId).orElse(null);
                    if (user != null) {
                        var subscription = userSubscriptionService.getUserSubscription(user);
                        if (subscription != null && subscription.getGroup() != null) {
                            subscriptionName = subscription.getGroup().getName();
                        }
                    }
                } catch (Exception e) {
                    log.debug("Could not fetch subscription for user {}: {}", userId, e.getMessage());
                }

                return DnsUserOverviewView.builder()
                    .userId(userId)
                    .email(email)
                    .username(username != null ? username : email.split("@")[0])
                    .enabledServicesCount(enabledCount)
                    .whitelistedIpsCount(whitelistCount)
                    .totalQueries(totalQueries)
                    .subscription(subscriptionName)
                    .build();
            })
            .collect(Collectors.toList());
    }

    public List<DnsUserRuleView> getAdminUserRules(int userId) {
        return userRuleRepository.findByUserId(userId).stream()
            .map(this::toUserRuleView)
            .collect(Collectors.toList());
    }

    public List<DnsWhitelistedIpView> getAdminUserWhitelist(int userId) {
        return whitelistedIpRepository.findByUserId(userId).stream()
            .map(this::toWhitelistedIpView)
            .collect(Collectors.toList());
    }

    @Transactional
    public DnsUserRuleView adminSetUserRule(DnsAdminUserRuleInput input) {
        User user = userRepository.findById(input.getUserId())
            .orElseThrow(() -> new NotFoundException("User not found"));

        DnsServiceRuleInput ruleInput = new DnsServiceRuleInput();
        ruleInput.setServiceId(input.getServiceId());
        ruleInput.setServiceType(input.getServiceType());
        ruleInput.setEnabled(input.isEnabled());
        ruleInput.setPreferredRegion(input.getPreferredRegion());

        return setUserRule(user, ruleInput);
    }

    @Transactional
    public DnsWhitelistedIpView adminWhitelistUserIp(DnsAdminWhitelistInput input) {
        User user = userRepository.findById(input.getUserId())
            .orElseThrow(() -> new NotFoundException("User not found"));

        DnsWhitelistIpInput ipInput = new DnsWhitelistIpInput();
        ipInput.setIpAddress(input.getIpAddress());
        ipInput.setLabel(input.getLabel());
        ipInput.setDeviceType(input.getDeviceType());
        ipInput.setExpiryDays(input.getExpiryDays());

        return whitelistIp(user, ipInput);
    }

    public List<DnsQueryLogView> getAdminQueryLogs(int limit, String serviceId, String region) {
        PageRequest pageable = PageRequest.of(0, limit);
        List<DnsQueryLog> logs;

        if (serviceId != null) {
            logs = queryLogRepository.findByServiceIdOrderByTimestampDesc(serviceId, pageable);
        } else if (region != null) {
            logs = queryLogRepository.findByRegionOrderByTimestampDesc(region, pageable);
        } else {
            logs = queryLogRepository.findAllOrderByTimestampDesc(pageable);
        }

        return logs.stream()
            .map(this::toQueryLogView)
            .collect(Collectors.toList());
    }

    // ========================================================================
    // SYNC TO GO SERVER
    // ========================================================================

    /**
     * Sync user rules to the Go DNS server.
     * This notifies the Go server to refresh its cache for this user.
     */
    private void syncUserRulesToServer(int userId) {
        if (orbmeshDnsSyncUrl == null || orbmeshDnsSyncUrl.isEmpty()) {
            log.debug("No OrbMesh DNS sync URL configured - skipping sync for user {}", userId);
            return;
        }

        try {
            String url = orbmeshDnsSyncUrl + "/dns/cache/invalidate/" + userId;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (orbmeshApiKey != null && !orbmeshApiKey.isEmpty()) {
                headers.set("X-API-Key", orbmeshApiKey);
            }

            HttpEntity<String> entity = new HttpEntity<>("{}", headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Successfully synced DNS rules to Go server for user {}", userId);
            } else {
                log.warn("Failed to sync DNS rules to Go server for user {}: HTTP {}",
                    userId, response.getStatusCode());
            }
        } catch (Exception e) {
            log.warn("Failed to sync DNS rules to Go server for user {}: {}",
                userId, e.getMessage());
        }
    }

    /**
     * Sync whitelist to the Go DNS server.
     * This notifies the Go server to refresh its whitelist cache.
     */
    private void syncWhitelistToServer(int userId) {
        if (orbmeshDnsSyncUrl == null || orbmeshDnsSyncUrl.isEmpty()) {
            log.debug("No OrbMesh DNS sync URL configured - skipping whitelist sync for user {}", userId);
            return;
        }

        try {
            String url = orbmeshDnsSyncUrl + "/dns/whitelist/refresh";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (orbmeshApiKey != null && !orbmeshApiKey.isEmpty()) {
                headers.set("X-API-Key", orbmeshApiKey);
            }

            HttpEntity<String> entity = new HttpEntity<>("{\"userId\":" + userId + "}", headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Successfully synced whitelist to Go server for user {}", userId);
            } else {
                log.warn("Failed to sync whitelist to Go server for user {}: HTTP {}",
                    userId, response.getStatusCode());
            }
        } catch (Exception e) {
            log.warn("Failed to sync whitelist to Go server for user {}: {}",
                userId, e.getMessage());
        }
    }

    /**
     * Get all active whitelisted IPs across all users.
     * Used by the Go server to build its whitelist.
     */
    public List<DnsWhitelistedIpView> getAllActiveWhitelistedIps() {
        return whitelistedIpRepository.findAllActive().stream()
            .map(this::toWhitelistedIpView)
            .collect(Collectors.toList());
    }

    /**
     * Set user rule from Go server (without re-syncing to avoid loops).
     * Called when the Go server notifies us of a rule change.
     */
    @Transactional
    public void setUserRuleFromGoServer(User user, String serviceId, DnsServiceType serviceType,
                                        boolean enabled, String region) {
        String serviceName = getServiceName(serviceId, serviceType);

        DnsUserRule rule = userRuleRepository
            .findByUserAndServiceIdAndServiceType(user, serviceId, serviceType)
            .orElse(new DnsUserRule());

        rule.setUser(user);
        rule.setServiceId(serviceId);
        rule.setServiceName(serviceName);
        rule.setServiceType(serviceType);
        rule.setEnabled(enabled);
        rule.setPreferredRegion(region);

        userRuleRepository.save(rule);
        log.info("Updated DNS rule from Go server for user {} service {} enabled={}",
            user.getId(), serviceId, enabled);
        // Note: Do not call syncUserRulesToServer here to avoid loops
    }

    // ========================================================================
    // VIEW CONVERTERS
    // ========================================================================

    private DnsUserRuleView toUserRuleView(DnsUserRule rule) {
        return DnsUserRuleView.builder()
            .id(rule.getId())
            .userId(rule.getUser().getId())
            .serviceId(rule.getServiceId())
            .serviceName(rule.getServiceName())
            .serviceType(rule.getServiceType())
            .enabled(rule.isEnabled())
            .preferredRegion(rule.getPreferredRegion())
            .createdAt(rule.getCreatedAt() != null ? rule.getCreatedAt().format(DATE_FORMATTER) : null)
            .updatedAt(rule.getUpdatedAt() != null ? rule.getUpdatedAt().format(DATE_FORMATTER) : null)
            .build();
    }

    private DnsWhitelistedIpView toWhitelistedIpView(DnsWhitelistedIp ip) {
        return DnsWhitelistedIpView.builder()
            .id(ip.getId())
            .userId(ip.getUser().getId())
            .ipAddress(ip.getIpAddress())
            .label(ip.getLabel())
            .deviceType(ip.getDeviceType())
            .active(ip.isActive())
            .lastUsed(ip.getLastUsed() != null ? ip.getLastUsed().format(DATE_FORMATTER) : null)
            .createdAt(ip.getCreatedAt() != null ? ip.getCreatedAt().format(DATE_FORMATTER) : null)
            .expiresAt(ip.getExpiresAt() != null ? ip.getExpiresAt().format(DATE_FORMATTER) : null)
            .build();
    }

    private DnsConfigView toConfigView(DnsConfig config) {
        return DnsConfigView.builder()
            .enabled(config.isEnabled())
            .primaryDns(config.getPrimaryDns())
            .secondaryDns(config.getSecondaryDns())
            .dohEnabled(config.isDohEnabled())
            .dohEndpoint(config.getDohEndpoint())
            .sniProxyEnabled(config.isSniProxyEnabled())
            .maxWhitelistedIps(config.getMaxWhitelistedIps())
            .whitelistExpiryDays(config.getWhitelistExpiryDays())
            .build();
    }

    private DnsQueryLogView toQueryLogView(DnsQueryLog log) {
        return DnsQueryLogView.builder()
            .id(log.getId())
            .userId(log.getUserId())
            .domain(log.getDomain())
            .serviceId(log.getServiceId())
            .region(log.getRegion())
            .responseType(log.getResponseType())
            .latencyMs(log.getLatencyMs() != null ? log.getLatencyMs() : 0)
            .timestamp(log.getTimestamp() != null ? log.getTimestamp().format(DATE_FORMATTER) : null)
            .build();
    }
}
