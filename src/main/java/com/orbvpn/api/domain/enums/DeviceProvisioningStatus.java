package com.orbvpn.api.domain.enums;

/**
 * Status of an OrbMesh device in the provisioning lifecycle.
 */
public enum DeviceProvisioningStatus {
    /**
     * Device identity created during manufacturing but not yet activated.
     * Device can be registered/activated.
     */
    PENDING,

    /**
     * Device has been successfully registered and is active on the network.
     * Cannot be registered again (prevents cloning).
     */
    ACTIVATED,

    /**
     * Device has been revoked by admin (stolen, returned, security issue).
     * Cannot be registered or used.
     */
    REVOKED,

    /**
     * Device was activated but then deactivated (e.g., customer returned it).
     * May be re-activated by admin after reset.
     */
    DEACTIVATED
}
