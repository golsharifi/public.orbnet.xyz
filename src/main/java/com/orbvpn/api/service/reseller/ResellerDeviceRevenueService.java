package com.orbvpn.api.service.reseller;

import com.orbvpn.api.domain.dto.ResellerDeviceRevenue;
import com.orbvpn.api.domain.dto.ResellerDeviceRevenue.DeviceRevenueByUser;
import com.orbvpn.api.domain.entity.Reseller;
import com.orbvpn.api.domain.entity.ResellerAddCredit;
import com.orbvpn.api.exception.NotFoundException;
import com.orbvpn.api.repository.ResellerAddCreditRepository;
import com.orbvpn.api.repository.ResellerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for generating reseller device addon revenue reports.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ResellerDeviceRevenueService {

    private final ResellerAddCreditRepository creditRepository;
    private final ResellerRepository resellerRepository;

    /**
     * Get device revenue report for a reseller.
     *
     * @param resellerId Optional reseller ID (null for current reseller)
     * @param startDate  Optional start date filter
     * @param endDate    Optional end date filter
     * @return Revenue report
     */
    public ResellerDeviceRevenue getDeviceRevenue(Integer resellerId, LocalDateTime startDate, LocalDateTime endDate) {
        Reseller reseller = resellerRepository.findById(resellerId)
                .orElseThrow(() -> new NotFoundException("Reseller not found"));

        // Default date range if not provided
        if (endDate == null) {
            endDate = LocalDateTime.now();
        }
        if (startDate == null) {
            startDate = endDate.minusMonths(12); // Default to last 12 months
        }

        // Get all device purchase transactions
        List<ResellerAddCredit> transactions = creditRepository.findByResellerAndTransactionTypeAndCreatedAtBetween(
                reseller,
                ResellerAddCredit.TYPE_DEVICE_PURCHASE,
                startDate,
                endDate);

        // Calculate totals
        int totalDevices = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        Map<String, DeviceRevenueByUser> userRevenueMap = new HashMap<>();

        for (ResellerAddCredit tx : transactions) {
            // Credit is stored as negative, so negate it for revenue
            BigDecimal amount = tx.getCredit().negate();
            totalRevenue = totalRevenue.add(amount);

            // Parse device count from reason (format: "Device addon purchase: X devices for email")
            int deviceCount = parseDeviceCount(tx.getReason());
            totalDevices += deviceCount;

            // Aggregate by user
            String userId = tx.getReferenceId();
            String userEmail = parseUserEmail(tx.getReason());

            if (userId != null) {
                userRevenueMap.compute(userId, (k, v) -> {
                    if (v == null) {
                        return DeviceRevenueByUser.builder()
                                .userId(Integer.parseInt(userId))
                                .userEmail(userEmail)
                                .devicesPurchased(deviceCount)
                                .totalSpent(amount)
                                .build();
                    }
                    v.setDevicesPurchased(v.getDevicesPurchased() + deviceCount);
                    v.setTotalSpent(v.getTotalSpent().add(amount));
                    return v;
                });
            }
        }

        // Calculate time-period revenues
        LocalDateTime now = LocalDateTime.now();
        BigDecimal revenueLastDay = calculateRevenueInPeriod(transactions, now.minusDays(1), now);
        BigDecimal revenueLastWeek = calculateRevenueInPeriod(transactions, now.minusWeeks(1), now);
        BigDecimal revenueLastMonth = calculateRevenueInPeriod(transactions, now.minusMonths(1), now);

        // Calculate average
        BigDecimal avgPrice = transactions.isEmpty() ? BigDecimal.ZERO
                : totalRevenue.divide(BigDecimal.valueOf(transactions.size()), 2, RoundingMode.HALF_UP);

        // Get top users by revenue
        List<DeviceRevenueByUser> topUsers = userRevenueMap.values().stream()
                .sorted((a, b) -> b.getTotalSpent().compareTo(a.getTotalSpent()))
                .limit(10)
                .collect(Collectors.toList());

        return ResellerDeviceRevenue.builder()
                .resellerId(reseller.getId())
                .resellerEmail(reseller.getUser().getEmail())
                .totalDevicesPurchased(totalDevices)
                .totalRevenue(totalRevenue)
                .revenueLastDay(revenueLastDay)
                .revenueLastWeek(revenueLastWeek)
                .revenueLastMonth(revenueLastMonth)
                .transactionCount(transactions.size())
                .averageDevicePrice(avgPrice)
                .topUsers(topUsers)
                .build();
    }

    /**
     * Calculate revenue within a time period.
     */
    private BigDecimal calculateRevenueInPeriod(List<ResellerAddCredit> transactions,
                                                 LocalDateTime start, LocalDateTime end) {
        return transactions.stream()
                .filter(tx -> tx.getCreatedAt() != null)
                .filter(tx -> tx.getCreatedAt().isAfter(start) && tx.getCreatedAt().isBefore(end))
                .map(tx -> tx.getCredit().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Parse device count from transaction reason.
     */
    private int parseDeviceCount(String reason) {
        if (reason == null) return 0;
        try {
            // Format: "Device addon purchase: X devices for email"
            if (reason.contains("devices for")) {
                String[] parts = reason.split(":");
                if (parts.length > 1) {
                    String countPart = parts[1].trim().split(" ")[0];
                    return Integer.parseInt(countPart);
                }
            }
        } catch (Exception e) {
            log.debug("Could not parse device count from reason: {}", reason);
        }
        return 1; // Default to 1 if parsing fails
    }

    /**
     * Parse user email from transaction reason.
     */
    private String parseUserEmail(String reason) {
        if (reason == null) return "unknown";
        try {
            // Format: "Device addon purchase: X devices for email"
            if (reason.contains("for ")) {
                return reason.substring(reason.lastIndexOf("for ") + 4).trim();
            }
        } catch (Exception e) {
            log.debug("Could not parse email from reason: {}", reason);
        }
        return "unknown";
    }

    /**
     * Get summary revenue for all resellers (admin view).
     */
    public List<ResellerDeviceRevenue> getAllResellersDeviceRevenue(LocalDateTime startDate, LocalDateTime endDate) {
        List<Reseller> resellers = resellerRepository.findAll();
        List<ResellerDeviceRevenue> revenues = new ArrayList<>();

        for (Reseller reseller : resellers) {
            try {
                revenues.add(getDeviceRevenue(reseller.getId(), startDate, endDate));
            } catch (Exception e) {
                log.warn("Failed to get revenue for reseller {}: {}", reseller.getId(), e.getMessage());
            }
        }

        return revenues.stream()
                .filter(r -> r.getTotalRevenue().compareTo(BigDecimal.ZERO) > 0)
                .sorted((a, b) -> b.getTotalRevenue().compareTo(a.getTotalRevenue()))
                .collect(Collectors.toList());
    }
}
