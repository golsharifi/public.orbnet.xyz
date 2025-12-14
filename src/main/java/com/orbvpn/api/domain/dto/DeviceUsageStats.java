package com.orbvpn.api.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for device usage statistics and analytics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceUsageStats {

    /**
     * User ID
     */
    private int userId;

    /**
     * User email
     */
    private String userEmail;

    /**
     * Number of devices allowed by subscription.
     */
    private int allowedDevices;

    /**
     * Number of currently active devices.
     */
    private int activeDevices;

    /**
     * Total devices ever registered.
     */
    private int totalDevicesRegistered;

    /**
     * Devices used in last 24 hours.
     */
    private int devicesLastDay;

    /**
     * Devices used in last 7 days.
     */
    private int devicesLastWeek;

    /**
     * Devices used in last 30 days.
     */
    private int devicesLastMonth;

    /**
     * Count of unique device models.
     */
    private int uniqueDeviceModels;

    /**
     * Count of unique operating systems.
     */
    private int uniqueOperatingSystems;

    /**
     * Average session duration in minutes.
     */
    private double averageSessionMinutes;

    /**
     * List of device models used.
     */
    private List<String> deviceModels;

    /**
     * List of operating systems used.
     */
    private List<String> operatingSystems;

    /**
     * List of suspicious patterns detected.
     */
    private List<String> suspiciousPatterns;

    /**
     * Whether suspicious activity was detected.
     */
    private boolean hasSuspiciousActivity;

    /**
     * Device utilization percentage (active/allowed * 100).
     */
    private double utilizationPercent;

    /**
     * Check if user is at device limit.
     */
    public boolean isAtDeviceLimit() {
        return activeDevices >= allowedDevices;
    }

    /**
     * Get remaining device slots.
     */
    public int getRemainingSlots() {
        return Math.max(0, allowedDevices - activeDevices);
    }
}
