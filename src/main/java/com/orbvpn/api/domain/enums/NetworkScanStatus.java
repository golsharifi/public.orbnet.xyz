package com.orbvpn.api.domain.enums;

/**
 * Status of a network scan operation.
 */
public enum NetworkScanStatus {
    PENDING,      // Scan queued but not started
    RUNNING,      // Scan in progress
    COMPLETED,    // Scan finished successfully
    FAILED,       // Scan failed with error
    CANCELLED     // Scan cancelled by user
}
