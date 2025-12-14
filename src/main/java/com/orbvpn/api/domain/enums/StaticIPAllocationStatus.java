package com.orbvpn.api.domain.enums;

/**
 * Status of a static IP allocation.
 */
public enum StaticIPAllocationStatus {
    PENDING,      // IP allocated but NAT not yet configured
    CONFIGURING,  // NAT configuration in progress
    ACTIVE,       // IP active and ready to use
    SUSPENDED,    // Temporarily suspended (payment issue, etc.)
    RELEASED      // IP released back to pool
}
