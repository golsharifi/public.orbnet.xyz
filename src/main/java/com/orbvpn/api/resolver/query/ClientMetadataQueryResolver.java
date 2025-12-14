package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.dto.ClientMetadataView;
import com.orbvpn.api.domain.entity.ClientMetadata;
import com.orbvpn.api.service.ClientMetadataService;
import com.orbvpn.api.service.LoginSecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

/**
 * GraphQL query resolver for client metadata.
 * Provides admin access to user device/location/platform analytics.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ClientMetadataQueryResolver {

    private final ClientMetadataService clientMetadataService;
    private final LoginSecurityService loginSecurityService;

    /**
     * Get paginated client metadata for a specific user
     */
    @Secured(ADMIN)
    @QueryMapping
    @Transactional(readOnly = true)
    public ClientMetadataPage getUserClientMetadata(
            @Argument Integer userId,
            @Argument Integer page,
            @Argument Integer size) {

        Page<ClientMetadata> result = clientMetadataService.getUserMetadata(
                userId,
                page != null ? page : 0,
                size != null ? size : 20);

        return toPage(result);
    }

    /**
     * Get signup metadata for a user (first signup event)
     */
    @Secured(ADMIN)
    @QueryMapping
    @Transactional(readOnly = true)
    public ClientMetadataView getUserSignupMetadata(@Argument Integer userId) {
        ClientMetadata metadata = clientMetadataService.getSignupMetadata(userId);
        return metadata != null ? toView(metadata) : null;
    }

    /**
     * Search client metadata with filters
     */
    @Secured(ADMIN)
    @QueryMapping
    @Transactional(readOnly = true)
    public ClientMetadataPage searchClientMetadata(
            @Argument Integer userId,
            @Argument String eventType,
            @Argument String platform,
            @Argument String countryCode,
            @Argument LocalDateTime startDate,
            @Argument LocalDateTime endDate,
            @Argument Integer page,
            @Argument Integer size) {

        Page<ClientMetadata> result = clientMetadataService.searchMetadata(
                userId,
                eventType,
                platform,
                countryCode,
                startDate,
                endDate,
                page != null ? page : 0,
                size != null ? size : 20);

        return toPage(result);
    }

    /**
     * Get client metadata analytics for a date range
     */
    @Secured(ADMIN)
    @QueryMapping
    @Transactional(readOnly = true)
    public ClientMetadataStats getClientMetadataStats(
            @Argument LocalDateTime startDate,
            @Argument LocalDateTime endDate) {

        ClientMetadataStats stats = new ClientMetadataStats();

        // Get various statistics
        Map<String, Long> countryStats = clientMetadataService.getSignupsByCountry(startDate, endDate);
        Map<String, Long> platformStats = clientMetadataService.getSignupsByPlatform(startDate, endDate);
        Map<String, Long> osStats = clientMetadataService.getSignupsByOS(startDate, endDate);

        stats.setCountryStats(toCountStats(countryStats));
        stats.setPlatformStats(toCountStats(platformStats));
        stats.setOsStats(toCountStats(osStats));
        stats.setBrowserStats(List.of()); // TODO: Add browser stats
        stats.setLanguageStats(List.of()); // TODO: Add language stats

        return stats;
    }

    /**
     * Get user security summary - login activity for the last 30 days
     * Shows unique IPs, countries, devices for security monitoring
     */
    @Secured(ADMIN)
    @QueryMapping
    @Transactional(readOnly = true)
    public UserSecuritySummaryView getUserSecuritySummary(@Argument Integer userId) {
        LoginSecurityService.UserSecuritySummary summary = loginSecurityService.getUserSecuritySummary(userId);

        UserSecuritySummaryView view = new UserSecuritySummaryView();
        view.setUniqueIPsLast30Days(summary.getUniqueIPsLast30Days());
        view.setUniqueCountriesLast30Days(summary.getUniqueCountriesLast30Days());
        view.setUniqueDevicesLast30Days(summary.getUniqueDevicesLast30Days());
        view.setRecentIPs(summary.getRecentIPs());
        view.setRecentCountries(summary.getRecentCountries());
        view.setRecentDeviceFingerprints(summary.getRecentDeviceFingerprints());
        view.setLastLoginTime(summary.getLastLoginTime());
        view.setLastLoginIP(summary.getLastLoginIP());
        view.setLastLoginCountry(summary.getLastLoginCountry());

        return view;
    }

    // ==================== Helper Methods ====================

    private ClientMetadataView toView(ClientMetadata metadata) {
        ClientMetadataView view = new ClientMetadataView();
        view.setId(metadata.getId());
        view.setUserId(metadata.getUser() != null ? metadata.getUser().getId() : null);
        view.setUserEmail(metadata.getUser() != null ? metadata.getUser().getEmail() : null);
        view.setEventType(metadata.getEventType());
        view.setIpAddress(metadata.getIpAddress());
        view.setCountryCode(metadata.getCountryCode());
        view.setCountryName(metadata.getCountryName());
        view.setRegion(metadata.getRegion());
        view.setCity(metadata.getCity());
        view.setLatitude(metadata.getLatitude());
        view.setLongitude(metadata.getLongitude());
        view.setTimezone(metadata.getTimezone());
        view.setIsp(metadata.getIsp());
        view.setPlatform(metadata.getPlatform());
        view.setOsName(metadata.getOsName());
        view.setOsVersion(metadata.getOsVersion());
        view.setDeviceType(metadata.getDeviceType());
        view.setDeviceManufacturer(metadata.getDeviceManufacturer());
        view.setDeviceModel(metadata.getDeviceModel());
        view.setScreenResolution(metadata.getScreenResolution());
        view.setBrowserName(metadata.getBrowserName());
        view.setBrowserVersion(metadata.getBrowserVersion());
        view.setUserAgent(metadata.getUserAgent());
        view.setAppVersion(metadata.getAppVersion());
        view.setAppBuild(metadata.getAppBuild());
        view.setAppIdentifier(metadata.getAppIdentifier());
        view.setLanguage(metadata.getLanguage());
        view.setAcceptedLanguages(metadata.getAcceptedLanguages());
        view.setLocale(metadata.getLocale());
        view.setReferrer(metadata.getReferrer());
        view.setUtmSource(metadata.getUtmSource());
        view.setUtmMedium(metadata.getUtmMedium());
        view.setUtmCampaign(metadata.getUtmCampaign());
        view.setCreatedAt(metadata.getCreatedAt());
        return view;
    }

    private ClientMetadataPage toPage(Page<ClientMetadata> page) {
        ClientMetadataPage result = new ClientMetadataPage();
        result.setContent(page.getContent().stream().map(this::toView).collect(Collectors.toList()));
        result.setTotalElements((int) page.getTotalElements());
        result.setTotalPages(page.getTotalPages());
        result.setPage(page.getNumber());
        result.setSize(page.getSize());
        return result;
    }

    private List<CountStat> toCountStats(Map<String, Long> stats) {
        return stats.entrySet().stream()
                .map(e -> new CountStat(e.getKey(), e.getValue().intValue()))
                .collect(Collectors.toList());
    }

    // ==================== Inner Classes for Response ====================

    public static class ClientMetadataPage {
        private List<ClientMetadataView> content;
        private int totalElements;
        private int totalPages;
        private int page;
        private int size;

        public List<ClientMetadataView> getContent() { return content; }
        public void setContent(List<ClientMetadataView> content) { this.content = content; }
        public int getTotalElements() { return totalElements; }
        public void setTotalElements(int totalElements) { this.totalElements = totalElements; }
        public int getTotalPages() { return totalPages; }
        public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
        public int getPage() { return page; }
        public void setPage(int page) { this.page = page; }
        public int getSize() { return size; }
        public void setSize(int size) { this.size = size; }
    }

    public static class ClientMetadataStats {
        private List<CountStat> countryStats;
        private List<CountStat> platformStats;
        private List<CountStat> osStats;
        private List<CountStat> browserStats;
        private List<CountStat> languageStats;

        public List<CountStat> getCountryStats() { return countryStats; }
        public void setCountryStats(List<CountStat> countryStats) { this.countryStats = countryStats; }
        public List<CountStat> getPlatformStats() { return platformStats; }
        public void setPlatformStats(List<CountStat> platformStats) { this.platformStats = platformStats; }
        public List<CountStat> getOsStats() { return osStats; }
        public void setOsStats(List<CountStat> osStats) { this.osStats = osStats; }
        public List<CountStat> getBrowserStats() { return browserStats; }
        public void setBrowserStats(List<CountStat> browserStats) { this.browserStats = browserStats; }
        public List<CountStat> getLanguageStats() { return languageStats; }
        public void setLanguageStats(List<CountStat> languageStats) { this.languageStats = languageStats; }
    }

    public static class CountStat {
        private String key;
        private int count;

        public CountStat(String key, int count) {
            this.key = key;
            this.count = count;
        }

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
    }

    public static class UserSecuritySummaryView {
        private int uniqueIPsLast30Days;
        private int uniqueCountriesLast30Days;
        private int uniqueDevicesLast30Days;
        private List<String> recentIPs;
        private List<String> recentCountries;
        private List<String> recentDeviceFingerprints;
        private LocalDateTime lastLoginTime;
        private String lastLoginIP;
        private String lastLoginCountry;

        public int getUniqueIPsLast30Days() { return uniqueIPsLast30Days; }
        public void setUniqueIPsLast30Days(int uniqueIPsLast30Days) { this.uniqueIPsLast30Days = uniqueIPsLast30Days; }
        public int getUniqueCountriesLast30Days() { return uniqueCountriesLast30Days; }
        public void setUniqueCountriesLast30Days(int uniqueCountriesLast30Days) { this.uniqueCountriesLast30Days = uniqueCountriesLast30Days; }
        public int getUniqueDevicesLast30Days() { return uniqueDevicesLast30Days; }
        public void setUniqueDevicesLast30Days(int uniqueDevicesLast30Days) { this.uniqueDevicesLast30Days = uniqueDevicesLast30Days; }
        public List<String> getRecentIPs() { return recentIPs; }
        public void setRecentIPs(List<String> recentIPs) { this.recentIPs = recentIPs; }
        public List<String> getRecentCountries() { return recentCountries; }
        public void setRecentCountries(List<String> recentCountries) { this.recentCountries = recentCountries; }
        public List<String> getRecentDeviceFingerprints() { return recentDeviceFingerprints; }
        public void setRecentDeviceFingerprints(List<String> recentDeviceFingerprints) { this.recentDeviceFingerprints = recentDeviceFingerprints; }
        public LocalDateTime getLastLoginTime() { return lastLoginTime; }
        public void setLastLoginTime(LocalDateTime lastLoginTime) { this.lastLoginTime = lastLoginTime; }
        public String getLastLoginIP() { return lastLoginIP; }
        public void setLastLoginIP(String lastLoginIP) { this.lastLoginIP = lastLoginIP; }
        public String getLastLoginCountry() { return lastLoginCountry; }
        public void setLastLoginCountry(String lastLoginCountry) { this.lastLoginCountry = lastLoginCountry; }
    }
}
