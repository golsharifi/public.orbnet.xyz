package com.orbvpn.api.domain.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity for tracking client metadata collected during user registration or login.
 * This data helps with analytics, security, and user experience improvements.
 *
 * Designed to be universal for all client types: web browsers, mobile apps (Flutter),
 * desktop applications, etc.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "client_metadata", indexes = {
    @Index(name = "idx_client_metadata_user_id", columnList = "user_id"),
    @Index(name = "idx_client_metadata_created_at", columnList = "created_at"),
    @Index(name = "idx_client_metadata_event_type", columnList = "event_type"),
    @Index(name = "idx_client_metadata_country_code", columnList = "country_code"),
    @Index(name = "idx_client_metadata_platform", columnList = "platform")
})
@EntityListeners(AuditingEntityListener.class)
public class ClientMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The user this metadata belongs to (can be null for pre-registration tracking)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // ==================== Event Information ====================

    /**
     * Type of event that triggered this metadata collection
     * Values: SIGNUP, LOGIN, APP_OPEN, PASSWORD_RESET, etc.
     */
    @Column(name = "event_type", length = 50, nullable = false)
    private String eventType;

    // ==================== IP & Location ====================

    /**
     * Client IP address (IPv4 or IPv6)
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /**
     * Country code derived from IP (ISO 3166-1 alpha-2, e.g., "US", "IR", "NL")
     */
    @Column(name = "country_code", length = 2)
    private String countryCode;

    /**
     * Country name derived from IP (e.g., "United States", "Iran", "Netherlands")
     */
    @Column(name = "country_name", length = 100)
    private String countryName;

    /**
     * Region/state derived from IP (e.g., "California", "Tehran")
     */
    @Column(name = "region", length = 100)
    private String region;

    /**
     * City derived from IP
     */
    @Column(name = "city", length = 100)
    private String city;

    /**
     * Latitude from IP geolocation
     */
    @Column(name = "latitude")
    private Double latitude;

    /**
     * Longitude from IP geolocation
     */
    @Column(name = "longitude")
    private Double longitude;

    /**
     * Timezone derived from IP or client (e.g., "America/Los_Angeles", "Asia/Tehran")
     */
    @Column(name = "timezone", length = 50)
    private String timezone;

    /**
     * ISP/Organization from IP lookup
     */
    @Column(name = "isp", length = 200)
    private String isp;

    // ==================== Network Information ====================

    /**
     * Connection type: WIFI, MOBILE, ETHERNET, VPN, NONE
     */
    @Column(name = "connection_type", length = 20)
    private String connectionType;

    /**
     * Mobile carrier name (e.g., "Verizon", "AT&T", "T-Mobile", "Irancell", "MCI")
     */
    @Column(name = "carrier_name", length = 100)
    private String carrierName;

    /**
     * Mobile network type (e.g., "4G", "5G", "LTE", "3G", "EDGE")
     */
    @Column(name = "mobile_network_type", length = 20)
    private String mobileNetworkType;

    // ==================== Platform & OS ====================

    /**
     * Platform type: WEB, IOS, ANDROID, WINDOWS, MACOS, LINUX
     */
    @Column(name = "platform", length = 20)
    private String platform;

    /**
     * Operating system name (e.g., "Windows", "macOS", "iOS", "Android", "Linux")
     */
    @Column(name = "os_name", length = 50)
    private String osName;

    /**
     * Operating system version (e.g., "14.0", "11", "10.15.7")
     */
    @Column(name = "os_version", length = 50)
    private String osVersion;

    // ==================== Device Information ====================

    /**
     * Device type: MOBILE, TABLET, DESKTOP, TV, UNKNOWN
     */
    @Column(name = "device_type", length = 20)
    private String deviceType;

    /**
     * Device manufacturer (e.g., "Apple", "Samsung", "Google")
     */
    @Column(name = "device_manufacturer", length = 100)
    private String deviceManufacturer;

    /**
     * Device model (e.g., "iPhone 15 Pro", "Galaxy S24", "Pixel 8")
     */
    @Column(name = "device_model", length = 100)
    private String deviceModel;

    /**
     * Unique device identifier (if available - e.g., IDFV for iOS, Android ID)
     * Note: This should be hashed for privacy
     */
    @Column(name = "device_id", length = 255)
    private String deviceId;

    /**
     * Screen resolution (e.g., "1920x1080", "390x844")
     */
    @Column(name = "screen_resolution", length = 20)
    private String screenResolution;

    // ==================== Browser Information (Web only) ====================

    /**
     * Browser name (e.g., "Chrome", "Firefox", "Safari")
     */
    @Column(name = "browser_name", length = 50)
    private String browserName;

    /**
     * Browser version (e.g., "120.0.6099.109")
     */
    @Column(name = "browser_version", length = 50)
    private String browserVersion;

    /**
     * Full user agent string (truncated to 500 chars)
     */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    // ==================== App Information (Mobile/Desktop apps) ====================

    /**
     * Application version (e.g., "1.2.3", "2.0.0-beta")
     */
    @Column(name = "app_version", length = 50)
    private String appVersion;

    /**
     * Application build number (e.g., "123", "456")
     */
    @Column(name = "app_build", length = 50)
    private String appBuild;

    /**
     * Application identifier/bundle ID (e.g., "com.orbvpn.app")
     */
    @Column(name = "app_identifier", length = 100)
    private String appIdentifier;

    // ==================== Language & Locale ====================

    /**
     * Preferred language from Accept-Language header or app settings
     * (e.g., "en-US", "fa-IR", "nl-NL")
     */
    @Column(name = "language", length = 20)
    private String language;

    /**
     * List of accepted languages (comma-separated, from Accept-Language header)
     */
    @Column(name = "accepted_languages", length = 500)
    private String acceptedLanguages;

    /**
     * Locale setting (e.g., "en_US", "fa_IR")
     */
    @Column(name = "locale", length = 20)
    private String locale;

    // ==================== Additional Context ====================

    /**
     * Referrer URL (where the user came from)
     */
    @Column(name = "referrer", length = 500)
    private String referrer;

    /**
     * UTM source for marketing attribution
     */
    @Column(name = "utm_source", length = 100)
    private String utmSource;

    /**
     * UTM medium for marketing attribution
     */
    @Column(name = "utm_medium", length = 100)
    private String utmMedium;

    /**
     * UTM campaign for marketing attribution
     */
    @Column(name = "utm_campaign", length = 100)
    private String utmCampaign;

    /**
     * Session ID for tracking user journey
     */
    @Column(name = "session_id", length = 100)
    private String sessionId;

    /**
     * Additional JSON data for extensibility
     */
    @Column(name = "extra_data", columnDefinition = "TEXT")
    private String extraData;

    // ==================== Timestamps ====================

    @Column(name = "created_at", nullable = false)
    @CreatedDate
    private LocalDateTime createdAt;

    // ==================== Event Type Constants ====================

    public static final String EVENT_SIGNUP = "SIGNUP";
    public static final String EVENT_LOGIN = "LOGIN";
    public static final String EVENT_APP_OPEN = "APP_OPEN";
    public static final String EVENT_PASSWORD_RESET = "PASSWORD_RESET";
    public static final String EVENT_PROFILE_UPDATE = "PROFILE_UPDATE";
    public static final String EVENT_SUBSCRIPTION_PURCHASE = "SUBSCRIPTION_PURCHASE";

    // ==================== Platform Constants ====================

    public static final String PLATFORM_WEB = "WEB";
    public static final String PLATFORM_IOS = "IOS";
    public static final String PLATFORM_ANDROID = "ANDROID";
    public static final String PLATFORM_WINDOWS = "WINDOWS";
    public static final String PLATFORM_MACOS = "MACOS";
    public static final String PLATFORM_LINUX = "LINUX";

    // ==================== Device Type Constants ====================

    public static final String DEVICE_MOBILE = "MOBILE";
    public static final String DEVICE_TABLET = "TABLET";
    public static final String DEVICE_DESKTOP = "DESKTOP";
    public static final String DEVICE_TV = "TV";
    public static final String DEVICE_UNKNOWN = "UNKNOWN";
}
