package com.orbvpn.api.service;

import com.orbvpn.api.domain.dto.ClientMetadataInput;
import com.orbvpn.api.domain.entity.ClientMetadata;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.repository.ClientMetadataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for recording and querying client metadata.
 * This service collects device, location, and platform information
 * from clients (web, mobile apps, desktop apps) for analytics and security purposes.
 */
@Service
@Slf4j
public class ClientMetadataService {

    private final ClientMetadataRepository clientMetadataRepository;
    private final GeoIPService geoIPService;
    private final ObjectProvider<LoginSecurityService> loginSecurityServiceProvider;

    public ClientMetadataService(
            ClientMetadataRepository clientMetadataRepository,
            GeoIPService geoIPService,
            ObjectProvider<LoginSecurityService> loginSecurityServiceProvider) {
        this.clientMetadataRepository = clientMetadataRepository;
        this.geoIPService = geoIPService;
        this.loginSecurityServiceProvider = loginSecurityServiceProvider;
    }

    /**
     * Record client metadata for a specific event.
     * This method enriches the provided input with server-detected information
     * like IP address and geolocation.
     *
     * @param user      The user (can be null for pre-registration events)
     * @param eventType The type of event (SIGNUP, LOGIN, APP_OPEN, etc.)
     * @param input     Client-provided metadata (can be null)
     * @return The saved ClientMetadata entity
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ClientMetadata recordMetadata(User user, String eventType, ClientMetadataInput input) {
        try {
            ClientMetadata metadata = new ClientMetadata();
            metadata.setUser(user);
            metadata.setEventType(eventType);
            metadata.setCreatedAt(LocalDateTime.now());

            // Get IP address from request
            String ipAddress = getClientIpAddress();
            metadata.setIpAddress(ipAddress);

            // Get user agent from request
            String userAgent = getUserAgent();
            metadata.setUserAgent(truncate(userAgent, 500));

            // Get accept-language from request
            String acceptLanguage = getAcceptLanguage();
            if (acceptLanguage != null && (input == null || input.getAcceptedLanguages() == null)) {
                metadata.setAcceptedLanguages(truncate(acceptLanguage, 500));
            }

            // Perform geolocation lookup
            if (ipAddress != null && !ipAddress.isEmpty()) {
                GeoIPService.GeoLocation geo = geoIPService.lookupIP(ipAddress);
                if (geo.isSuccess()) {
                    metadata.setCountryCode(geo.getCountryCode());
                    metadata.setCountryName(geo.getCountryName());
                    metadata.setRegion(geo.getRegion());
                    metadata.setCity(geo.getCity());
                    metadata.setLatitude(geo.getLatitude());
                    metadata.setLongitude(geo.getLongitude());
                    metadata.setIsp(truncate(geo.getIsp(), 200));

                    // Use geo timezone if client didn't provide one
                    if ((input == null || input.getTimezone() == null) && geo.getTimezone() != null) {
                        metadata.setTimezone(geo.getTimezone());
                    }
                }
            }

            // Copy client-provided metadata (if available)
            if (input != null) {
                copyClientInput(metadata, input);
            }

            // Parse user agent for browser/OS info if not provided by client
            if (userAgent != null && !userAgent.isEmpty()) {
                parseUserAgentIfNeeded(metadata, userAgent);
            }

            ClientMetadata saved = clientMetadataRepository.save(metadata);
            log.info("Recorded client metadata for event {} user {} from IP {} ({})",
                    eventType,
                    user != null ? user.getEmail() : "anonymous",
                    ipAddress,
                    metadata.getCountryCode());

            return saved;
        } catch (Exception e) {
            log.error("Failed to record client metadata: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Record metadata asynchronously (for non-critical logging)
     */
    @Async
    public void recordMetadataAsync(User user, String eventType, ClientMetadataInput input) {
        recordMetadata(user, eventType, input);
    }

    /**
     * Record signup metadata
     */
    public ClientMetadata recordSignup(User user, ClientMetadataInput input) {
        return recordMetadata(user, ClientMetadata.EVENT_SIGNUP, input);
    }

    /**
     * Record login metadata and trigger security analysis
     */
    public ClientMetadata recordLogin(User user, ClientMetadataInput input) {
        ClientMetadata metadata = recordMetadata(user, ClientMetadata.EVENT_LOGIN, input);

        // Trigger security analysis (won't block login - analysis is read-only)
        if (metadata != null && user != null) {
            try {
                LoginSecurityService securityService = loginSecurityServiceProvider.getIfAvailable();
                if (securityService != null) {
                    LoginSecurityService.LoginSecurityResult result =
                        securityService.analyzeLoginAndNotify(user, metadata);
                    if (result.isNotificationSent()) {
                        log.info("Security notification sent for user {} - flags: {}",
                            user.getEmail(), result.getSecurityFlags());
                    }
                }
            } catch (Exception e) {
                // Don't fail login due to security analysis error
                log.error("Failed to analyze login security for user {}: {}",
                    user.getEmail(), e.getMessage());
            }
        }

        return metadata;
    }

    /**
     * Record app open metadata
     */
    public ClientMetadata recordAppOpen(User user, ClientMetadataInput input) {
        return recordMetadata(user, ClientMetadata.EVENT_APP_OPEN, input);
    }

    /**
     * Get metadata for a user
     */
    @Transactional(readOnly = true)
    public Page<ClientMetadata> getUserMetadata(int userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return clientMetadataRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    /**
     * Get signup metadata for a user
     */
    @Transactional(readOnly = true)
    public ClientMetadata getSignupMetadata(int userId) {
        List<ClientMetadata> results = clientMetadataRepository.findSignupMetadataByUser(userId);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Search metadata with filters
     */
    @Transactional(readOnly = true)
    public Page<ClientMetadata> searchMetadata(
            Integer userId,
            String eventType,
            String platform,
            String countryCode,
            LocalDateTime startDate,
            LocalDateTime endDate,
            int page,
            int size) {
        Pageable pageable = PageRequest.of(page, size);
        return clientMetadataRepository.searchMetadata(
                userId, eventType, platform, countryCode, startDate, endDate, pageable);
    }

    /**
     * Get signup analytics by country
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getSignupsByCountry(LocalDateTime startDate, LocalDateTime endDate) {
        List<Object[]> results = clientMetadataRepository.countSignupsByCountry(startDate, endDate);
        return results.stream()
                .filter(r -> r[0] != null)
                .collect(Collectors.toMap(
                        r -> (String) r[0],
                        r -> (Long) r[2]));
    }

    /**
     * Get signup analytics by platform
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getSignupsByPlatform(LocalDateTime startDate, LocalDateTime endDate) {
        List<Object[]> results = clientMetadataRepository.countSignupsByPlatform(startDate, endDate);
        return results.stream()
                .filter(r -> r[0] != null)
                .collect(Collectors.toMap(
                        r -> (String) r[0],
                        r -> (Long) r[1]));
    }

    /**
     * Get signup analytics by OS
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getSignupsByOS(LocalDateTime startDate, LocalDateTime endDate) {
        List<Object[]> results = clientMetadataRepository.countSignupsByOS(startDate, endDate);
        return results.stream()
                .filter(r -> r[0] != null)
                .collect(Collectors.toMap(
                        r -> (String) r[0],
                        r -> (Long) r[1]));
    }

    /**
     * Get recent login IPs for a user (security feature)
     */
    @Transactional(readOnly = true)
    public List<String> getRecentLoginIPs(int userId, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return clientMetadataRepository.findRecentLoginIpsByUser(userId, since);
    }

    /**
     * Get recent login countries for a user (security feature)
     */
    @Transactional(readOnly = true)
    public List<String> getRecentLoginCountries(int userId, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return clientMetadataRepository.findRecentLoginCountriesByUser(userId, since);
    }

    // ==================== Helper Methods ====================

    private void copyClientInput(ClientMetadata metadata, ClientMetadataInput input) {
        // Platform & OS
        if (input.getPlatform() != null) metadata.setPlatform(input.getPlatform());
        if (input.getOsName() != null) metadata.setOsName(input.getOsName());
        if (input.getOsVersion() != null) metadata.setOsVersion(input.getOsVersion());

        // Device
        if (input.getDeviceType() != null) metadata.setDeviceType(input.getDeviceType());
        if (input.getDeviceManufacturer() != null) metadata.setDeviceManufacturer(input.getDeviceManufacturer());
        if (input.getDeviceModel() != null) metadata.setDeviceModel(input.getDeviceModel());
        if (input.getDeviceId() != null) metadata.setDeviceId(input.getDeviceId());
        if (input.getScreenResolution() != null) metadata.setScreenResolution(input.getScreenResolution());

        // Browser
        if (input.getBrowserName() != null) metadata.setBrowserName(input.getBrowserName());
        if (input.getBrowserVersion() != null) metadata.setBrowserVersion(input.getBrowserVersion());
        if (input.getUserAgent() != null) metadata.setUserAgent(truncate(input.getUserAgent(), 500));

        // App
        if (input.getAppVersion() != null) metadata.setAppVersion(input.getAppVersion());
        if (input.getAppBuild() != null) metadata.setAppBuild(input.getAppBuild());
        if (input.getAppIdentifier() != null) metadata.setAppIdentifier(input.getAppIdentifier());

        // Language & Locale
        if (input.getLanguage() != null) metadata.setLanguage(input.getLanguage());
        if (input.getAcceptedLanguages() != null) metadata.setAcceptedLanguages(truncate(input.getAcceptedLanguages(), 500));
        if (input.getLocale() != null) metadata.setLocale(input.getLocale());
        if (input.getTimezone() != null) metadata.setTimezone(input.getTimezone());

        // Network Information
        if (input.getConnectionType() != null) metadata.setConnectionType(input.getConnectionType());
        if (input.getCarrierName() != null) metadata.setCarrierName(input.getCarrierName());
        if (input.getMobileNetworkType() != null) metadata.setMobileNetworkType(input.getMobileNetworkType());

        // Marketing
        if (input.getReferrer() != null) metadata.setReferrer(truncate(input.getReferrer(), 500));
        if (input.getUtmSource() != null) metadata.setUtmSource(input.getUtmSource());
        if (input.getUtmMedium() != null) metadata.setUtmMedium(input.getUtmMedium());
        if (input.getUtmCampaign() != null) metadata.setUtmCampaign(input.getUtmCampaign());
        if (input.getSessionId() != null) metadata.setSessionId(input.getSessionId());

        // Extra
        if (input.getExtraData() != null) metadata.setExtraData(input.getExtraData());
    }

    private void parseUserAgentIfNeeded(ClientMetadata metadata, String userAgent) {
        // Only parse if client didn't provide browser/OS info
        if (metadata.getBrowserName() == null && metadata.getOsName() == null) {
            // Simple user agent parsing - for production, use a library like ua-parser
            String ua = userAgent.toLowerCase();

            // Detect OS
            if (ua.contains("windows")) {
                metadata.setOsName("Windows");
                metadata.setPlatform(ClientMetadata.PLATFORM_WEB);
            } else if (ua.contains("mac os x") || ua.contains("macintosh")) {
                metadata.setOsName("macOS");
                metadata.setPlatform(ClientMetadata.PLATFORM_WEB);
            } else if (ua.contains("iphone") || ua.contains("ipad")) {
                metadata.setOsName("iOS");
                metadata.setPlatform(ClientMetadata.PLATFORM_WEB);
                metadata.setDeviceType(ua.contains("ipad") ? ClientMetadata.DEVICE_TABLET : ClientMetadata.DEVICE_MOBILE);
            } else if (ua.contains("android")) {
                metadata.setOsName("Android");
                metadata.setPlatform(ClientMetadata.PLATFORM_WEB);
                metadata.setDeviceType(ua.contains("tablet") ? ClientMetadata.DEVICE_TABLET : ClientMetadata.DEVICE_MOBILE);
            } else if (ua.contains("linux")) {
                metadata.setOsName("Linux");
                metadata.setPlatform(ClientMetadata.PLATFORM_WEB);
            }

            // Detect browser
            if (ua.contains("chrome") && !ua.contains("edg")) {
                metadata.setBrowserName("Chrome");
            } else if (ua.contains("firefox")) {
                metadata.setBrowserName("Firefox");
            } else if (ua.contains("safari") && !ua.contains("chrome")) {
                metadata.setBrowserName("Safari");
            } else if (ua.contains("edg")) {
                metadata.setBrowserName("Edge");
            } else if (ua.contains("opera") || ua.contains("opr")) {
                metadata.setBrowserName("Opera");
            }

            // Set device type if not already set
            if (metadata.getDeviceType() == null) {
                if (ua.contains("mobile")) {
                    metadata.setDeviceType(ClientMetadata.DEVICE_MOBILE);
                } else {
                    metadata.setDeviceType(ClientMetadata.DEVICE_DESKTOP);
                }
            }
        }
    }

    private String getClientIpAddress() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();

                // Check for forwarded IP (proxy/load balancer)
                String forwardedFor = request.getHeader("X-Forwarded-For");
                if (forwardedFor != null && !forwardedFor.isEmpty()) {
                    return forwardedFor.split(",")[0].trim();
                }

                String realIp = request.getHeader("X-Real-IP");
                if (realIp != null && !realIp.isEmpty()) {
                    return realIp;
                }

                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            log.debug("Could not get client IP: {}", e.getMessage());
        }
        return null;
    }

    private String getUserAgent() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                return request.getHeader("User-Agent");
            }
        } catch (Exception e) {
            log.debug("Could not get user agent: {}", e.getMessage());
        }
        return null;
    }

    private String getAcceptLanguage() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                return request.getHeader("Accept-Language");
            }
        } catch (Exception e) {
            log.debug("Could not get Accept-Language: {}", e.getMessage());
        }
        return null;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
