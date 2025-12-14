package com.orbvpn.api.domain.enums;

/**
 * Type of network scan to perform.
 */
public enum NetworkScanType {
    QUICK,     // Fast ping sweep only
    NORMAL,    // Ping sweep with basic info
    DEEP       // Full scan with port scanning and service detection
}
