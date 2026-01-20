package com.orbvpn.api.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Service for IP-to-geolocation lookups using free IP geolocation APIs.
 * Falls back gracefully if the service is unavailable.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GeoIPService {

    private final RestTemplate restTemplate;

    /**
     * Response from ip-api.com
     */
    @Data
    public static class GeoIPResponse {
        private String status;
        private String country;
        private String countryCode;
        private String region;
        private String regionName;
        private String city;
        private Double lat;
        private Double lon;
        private String timezone;
        private String isp;
        private String org;
        private String as;
        private String query; // IP address
    }

    /**
     * Result of geolocation lookup
     */
    @Data
    public static class GeoLocation {
        private String countryCode;
        private String countryName;
        private String region;
        private String city;
        private Double latitude;
        private Double longitude;
        private String timezone;
        private String isp;
        private String ipAddress;
        private boolean success;

        public static GeoLocation empty() {
            GeoLocation location = new GeoLocation();
            location.setSuccess(false);
            return location;
        }
    }

    /**
     * Look up geolocation for an IP address using ip-api.com (free tier: 45 requests/minute)
     *
     * @param ipAddress The IP address to look up
     * @return GeoLocation with location details, or empty GeoLocation if lookup fails
     */
    public GeoLocation lookupIP(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty() || isPrivateIP(ipAddress)) {
            log.debug("Skipping geolocation lookup for null, empty, or private IP: {}", ipAddress);
            return GeoLocation.empty();
        }

        try {
            // Using ip-api.com free tier (no API key needed, 45 req/min limit)
            // For production, consider using MaxMind GeoIP2 database for better accuracy and no rate limits
            String url = String.format("http://ip-api.com/json/%s?fields=status,country,countryCode,region,regionName,city,lat,lon,timezone,isp", ipAddress);

            GeoIPResponse response = restTemplate.getForObject(url, GeoIPResponse.class);

            if (response != null && "success".equals(response.getStatus())) {
                GeoLocation location = new GeoLocation();
                location.setCountryCode(response.getCountryCode());
                location.setCountryName(response.getCountry());
                location.setRegion(response.getRegionName());
                location.setCity(response.getCity());
                location.setLatitude(response.getLat());
                location.setLongitude(response.getLon());
                location.setTimezone(response.getTimezone());
                location.setIsp(response.getIsp());
                location.setIpAddress(ipAddress);
                location.setSuccess(true);

                log.debug("GeoIP lookup successful for IP {}: {} ({})", ipAddress, response.getCountry(), response.getCountryCode());
                return location;
            } else {
                log.warn("GeoIP lookup failed for IP {}: status={}", ipAddress, response != null ? response.getStatus() : "null response");
                return GeoLocation.empty();
            }
        } catch (Exception e) {
            log.warn("GeoIP lookup error for IP {}: {}", ipAddress, e.getMessage());
            return GeoLocation.empty();
        }
    }

    /**
     * Check if an IP address is private/local
     */
    private boolean isPrivateIP(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return true;
        }

        // Localhost
        if ("127.0.0.1".equals(ipAddress) || "::1".equals(ipAddress) || "localhost".equalsIgnoreCase(ipAddress)) {
            return true;
        }

        // Check for private IPv4 ranges
        if (ipAddress.startsWith("10.") ||
            ipAddress.startsWith("172.16.") || ipAddress.startsWith("172.17.") ||
            ipAddress.startsWith("172.18.") || ipAddress.startsWith("172.19.") ||
            ipAddress.startsWith("172.20.") || ipAddress.startsWith("172.21.") ||
            ipAddress.startsWith("172.22.") || ipAddress.startsWith("172.23.") ||
            ipAddress.startsWith("172.24.") || ipAddress.startsWith("172.25.") ||
            ipAddress.startsWith("172.26.") || ipAddress.startsWith("172.27.") ||
            ipAddress.startsWith("172.28.") || ipAddress.startsWith("172.29.") ||
            ipAddress.startsWith("172.30.") || ipAddress.startsWith("172.31.") ||
            ipAddress.startsWith("192.168.")) {
            return true;
        }

        return false;
    }
}
