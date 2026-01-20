package com.orbvpn.api.domain.enums;

/**
 * Deployment types for OrbMesh nodes.
 */
public enum DeploymentType {
    ORBVPN_DC,   // OrbVPN managed Azure datacenter
    PARTNER_DC,  // Third-party partner datacenter
    HOME         // User home device
}
