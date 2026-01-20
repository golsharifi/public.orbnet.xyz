package com.orbvpn.api.domain.enums;

/**
 * Status of a port forwarding rule.
 */
public enum PortForwardStatus {
    PENDING,     // Rule created but not yet configured
    CONFIGURING, // Rule being applied to server
    ACTIVE,      // Rule active and working
    DISABLED,    // Rule disabled by user
    ERROR,       // Configuration error
    DELETED      // Rule deleted
}
