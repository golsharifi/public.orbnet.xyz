package com.orbvpn.api.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for reseller device revenue reporting.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResellerDeviceRevenue {

    private int resellerId;
    private String resellerEmail;
    private int totalDevicesPurchased;
    private BigDecimal totalRevenue;
    private BigDecimal revenueLastDay;
    private BigDecimal revenueLastWeek;
    private BigDecimal revenueLastMonth;
    private int transactionCount;
    private BigDecimal averageDevicePrice;
    private List<DeviceRevenueByUser> topUsers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceRevenueByUser {
        private int userId;
        private String userEmail;
        private int devicesPurchased;
        private BigDecimal totalSpent;
    }
}
