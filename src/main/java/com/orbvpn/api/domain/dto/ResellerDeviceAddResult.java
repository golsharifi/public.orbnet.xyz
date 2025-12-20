package com.orbvpn.api.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Result DTO for reseller device addon operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResellerDeviceAddResult {

    /**
     * Whether the operation was successful.
     */
    private boolean success;

    /**
     * The user ID that received the devices.
     */
    private int userId;

    /**
     * The user's email.
     */
    private String userEmail;

    /**
     * Number of devices added (can be negative for reductions).
     */
    private int devicesAdded;

    /**
     * Total device count after the operation.
     */
    private int totalDevices;

    /**
     * Amount charged to the reseller.
     */
    private BigDecimal amountCharged;

    /**
     * Reseller's new credit balance.
     */
    private BigDecimal resellerNewBalance;

    /**
     * Human-readable message.
     */
    private String message;

    /**
     * Error code if operation failed.
     */
    private String errorCode;
}
