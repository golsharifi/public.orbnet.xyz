package com.orbvpn.api.domain.dto;

import lombok.Data;

/**
 * Input DTO for collecting client metadata from any platform (web, iOS, Android, desktop).
 * All fields are optional - clients should send whatever data they have available.
 * The server will automatically detect and fill in additional data like IP geolocation.
 *
 * This DTO is designed to be backward compatible - new fields can be added without
 * breaking existing clients.
 */
@Data
public class ClientMetadataInput {

    // ==================== Platform & OS ====================

    /**
     * Platform type: WEB, IOS, ANDROID, WINDOWS, MACOS, LINUX
     */
    private String platform;

    /**
     * Operating system name (e.g., "Windows", "macOS", "iOS", "Android", "Linux")
     */
    private String osName;

    /**
     * Operating system version (e.g., "14.0", "11", "10.15.7")
     */
    private String osVersion;

    // ==================== Device Information ====================

    /**
     * Device type: MOBILE, TABLET, DESKTOP, TV, UNKNOWN
     */
    private String deviceType;

    /**
     * Device manufacturer (e.g., "Apple", "Samsung", "Google")
     */
    private String deviceManufacturer;

    /**
     * Device model (e.g., "iPhone 15 Pro", "Galaxy S24", "Pixel 8")
     */
    private String deviceModel;

    /**
     * Unique device identifier (hashed for privacy)
     */
    private String deviceId;

    /**
     * Screen resolution (e.g., "1920x1080", "390x844")
     */
    private String screenResolution;

    // ==================== Browser Information (Web only) ====================

    /**
     * Browser name (e.g., "Chrome", "Firefox", "Safari")
     */
    private String browserName;

    /**
     * Browser version (e.g., "120.0.6099.109")
     */
    private String browserVersion;

    /**
     * Full user agent string
     */
    private String userAgent;

    // ==================== App Information (Mobile/Desktop apps) ====================

    /**
     * Application version (e.g., "1.2.3", "2.0.0-beta")
     */
    private String appVersion;

    /**
     * Application build number (e.g., "123", "456")
     */
    private String appBuild;

    /**
     * Application identifier/bundle ID (e.g., "com.orbvpn.app")
     */
    private String appIdentifier;

    // ==================== Language & Locale ====================

    /**
     * Preferred language (e.g., "en-US", "fa-IR", "nl-NL")
     */
    private String language;

    /**
     * List of accepted languages (comma-separated)
     */
    private String acceptedLanguages;

    /**
     * Locale setting (e.g., "en_US", "fa_IR")
     */
    private String locale;

    /**
     * Timezone (e.g., "America/Los_Angeles", "Asia/Tehran")
     */
    private String timezone;

    // ==================== Network Information ====================

    /**
     * Connection type: WIFI, MOBILE, ETHERNET, VPN, NONE
     */
    private String connectionType;

    /**
     * Mobile carrier name (e.g., "Verizon", "AT&T", "T-Mobile", "Irancell", "MCI")
     */
    private String carrierName;

    /**
     * Mobile network type (e.g., "4G", "5G", "LTE", "3G", "EDGE")
     */
    private String mobileNetworkType;

    // ==================== Marketing Attribution ====================

    /**
     * Referrer URL
     */
    private String referrer;

    /**
     * UTM source
     */
    private String utmSource;

    /**
     * UTM medium
     */
    private String utmMedium;

    /**
     * UTM campaign
     */
    private String utmCampaign;

    /**
     * Session ID for tracking user journey
     */
    private String sessionId;

    // ==================== Additional Data ====================

    /**
     * FCM token for push notifications (mobile apps)
     */
    private String fcmToken;

    /**
     * Additional JSON data for extensibility
     */
    private String extraData;
}
