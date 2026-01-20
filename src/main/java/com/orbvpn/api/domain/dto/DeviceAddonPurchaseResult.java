package com.orbvpn.api.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result DTO for device addon in-app purchases.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceAddonPurchaseResult {

    /**
     * Whether the purchase was successful.
     */
    private boolean success;

    /**
     * User ID that received the devices.
     */
    private int userId;

    /**
     * Number of devices added.
     */
    private int devicesAdded;

    /**
     * Total device count after purchase.
     */
    private int totalDevices;

    /**
     * Payment/transaction ID.
     */
    private String paymentId;

    /**
     * Human-readable message.
     */
    private String message;

    /**
     * Error code if failed.
     */
    private String errorCode;
}
