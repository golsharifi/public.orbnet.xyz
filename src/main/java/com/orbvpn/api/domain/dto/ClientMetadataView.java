package com.orbvpn.api.domain.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * View DTO for ClientMetadata entity.
 * Used for GraphQL responses.
 */
@Data
public class ClientMetadataView {
    private Long id;
    private Integer userId;
    private String userEmail;

    // Event
    private String eventType;

    // IP & Location
    private String ipAddress;
    private String countryCode;
    private String countryName;
    private String region;
    private String city;
    private Double latitude;
    private Double longitude;
    private String timezone;
    private String isp;

    // Network
    private String connectionType;
    private String carrierName;
    private String mobileNetworkType;

    // Platform & OS
    private String platform;
    private String osName;
    private String osVersion;

    // Device
    private String deviceType;
    private String deviceManufacturer;
    private String deviceModel;
    private String screenResolution;

    // Browser
    private String browserName;
    private String browserVersion;
    private String userAgent;

    // App
    private String appVersion;
    private String appBuild;
    private String appIdentifier;

    // Language
    private String language;
    private String acceptedLanguages;
    private String locale;

    // Marketing
    private String referrer;
    private String utmSource;
    private String utmMedium;
    private String utmCampaign;

    // Timestamps
    private LocalDateTime createdAt;
}
