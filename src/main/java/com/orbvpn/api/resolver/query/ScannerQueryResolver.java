package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.dto.scanner.*;
import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.domain.enums.*;
import com.orbvpn.api.repository.*;
import com.orbvpn.api.service.UserService;
import com.orbvpn.api.service.scanner.NetworkScannerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;
import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ScannerQueryResolver {

    private final NetworkScannerService scannerService;
    private final NetworkScanRepository scanRepository;
    private final DiscoveredDeviceRepository deviceRepository;
    private final UserService userService;

    // ========== USER QUERIES ==========

    @Secured(USER)
    @QueryMapping
    public Map<String, Object> scannerDashboard() {
        User user = userService.getUser();
        Page<NetworkScan> recentScans = scanRepository.findByUserOrderByCreatedAtDesc(
                user, PageRequest.of(0, 5));

        Optional<NetworkScan> latestScan = scannerService.getLatestScan();
        long knownDevices = deviceRepository.findByUserOrderByLastSeenAtDesc(
                user, PageRequest.of(0, 1)).getTotalElements();

        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("latestScan", latestScan.orElse(null));
        dashboard.put("recentScans", recentScans.getContent());
        dashboard.put("knownDevices", knownDevices);
        dashboard.put("currentSecurityScore", latestScan.map(this::buildSecurityScore).orElse(null));
        dashboard.put("hasActiveSubscription", true); // Network scanner is FREE

        return dashboard;
    }

    @Secured(USER)
    @QueryMapping
    public NetworkScan networkScan(@Argument String scanId) {
        return scannerService.getScanById(scanId)
                .orElseThrow(() -> new IllegalArgumentException("Scan not found: " + scanId));
    }

    @Secured(USER)
    @QueryMapping
    public Map<String, Object> myScanHistory(@Argument Integer page, @Argument Integer size) {
        int pageNum = page != null ? page : 0;
        int pageSize = size != null ? size : 10;

        Page<NetworkScan> scans = scannerService.getScanHistory(pageNum, pageSize);

        return Map.of(
                "content", scans.getContent(),
                "totalElements", scans.getTotalElements(),
                "totalPages", scans.getTotalPages(),
                "number", scans.getNumber(),
                "size", scans.getSize()
        );
    }

    @Secured(USER)
    @QueryMapping
    public List<DiscoveredDevice> scanDevices(@Argument String scanId) {
        return scannerService.getScanDevices(scanId);
    }

    @Secured(USER)
    @QueryMapping
    public Map<String, Object> myDevices(@Argument Integer page, @Argument Integer size) {
        int pageNum = page != null ? page : 0;
        int pageSize = size != null ? size : 20;

        Page<DiscoveredDevice> devices = scannerService.getUserDevices(pageNum, pageSize);

        return Map.of(
                "content", devices.getContent(),
                "totalElements", devices.getTotalElements(),
                "totalPages", devices.getTotalPages(),
                "number", devices.getNumber(),
                "size", devices.getSize()
        );
    }

    @Secured(USER)
    @QueryMapping
    public Map<String, Object> mySecurityScore() {
        Optional<NetworkScan> latestScan = scannerService.getLatestScan();
        if (latestScan.isEmpty()) {
            return Map.of(
                    "score", 0,
                    "grade", "N/A",
                    "vulnerableDevices", 0,
                    "recommendations", Collections.emptyList(),
                    "lastUpdated", LocalDateTime.now()
            );
        }
        return buildSecurityScore(latestScan.get());
    }

    // ========== ADMIN QUERIES ==========

    @Secured(ADMIN)
    @QueryMapping
    public Map<String, Object> adminScannerStats() {
        ScannerStatsDTO stats = scannerService.getAdminStats();

        // Get device type distribution
        List<Object[]> deviceTypes = deviceRepository.getGlobalDeviceTypeDistribution();
        List<Map<String, Object>> deviceTypeList = deviceTypes.stream()
                .map(row -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("deviceType", row[0].toString());
                    map.put("count", ((Number) row[1]).intValue());
                    return map;
                })
                .collect(Collectors.toList());

        // Get top vendors
        List<Object[]> vendors = deviceRepository.getGlobalVendorDistribution(PageRequest.of(0, 10));
        List<Map<String, Object>> vendorList = vendors.stream()
                .map(row -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("vendor", row[0].toString());
                    map.put("count", ((Number) row[1]).intValue());
                    return map;
                })
                .collect(Collectors.toList());

        // Convert grade distribution
        List<Map<String, Object>> gradeList = stats.getGradeDistribution() != null ?
                stats.getGradeDistribution().entrySet().stream()
                        .map(e -> {
                            Map<String, Object> map = new HashMap<>();
                            map.put("grade", e.getKey());
                            map.put("count", e.getValue().intValue());
                            return map;
                        })
                        .collect(Collectors.toList()) :
                Collections.emptyList();

        return Map.of(
                "totalScans", stats.getTotalScans(),
                "scansToday", stats.getScansToday(),
                "scansThisWeek", stats.getScansThisWeek(),
                "averageSecurityScore", stats.getAverageSecurityScore() != null ? stats.getAverageSecurityScore() : 0.0,
                "gradeDistribution", gradeList,
                "deviceTypeDistribution", deviceTypeList,
                "topVendors", vendorList
        );
    }

    @Secured(ADMIN)
    @QueryMapping
    public Map<String, Object> adminAllScans(@Argument Integer page, @Argument Integer size, @Argument NetworkScanStatus status) {
        int pageNum = page != null ? page : 0;
        int pageSize = size != null ? size : 20;

        Page<NetworkScan> scans = scannerService.getAllScans(pageNum, pageSize);

        return Map.of(
                "content", scans.getContent(),
                "totalElements", scans.getTotalElements(),
                "totalPages", scans.getTotalPages(),
                "number", scans.getNumber(),
                "size", scans.getSize()
        );
    }

    // ========== HELPER METHODS ==========

    private Map<String, Object> buildSecurityScore(NetworkScan scan) {
        List<Map<String, Object>> recommendations = new ArrayList<>();

        // Generate recommendations based on scan results
        if (scan.getVulnerableDevices() != null && scan.getVulnerableDevices() > 0) {
            recommendations.add(Map.of(
                    "severity", "HIGH",
                    "title", "Vulnerable devices detected",
                    "description", "Some devices have open ports that could be exploited. Consider closing unnecessary ports.",
                    "affectedDevices", scan.getDevices().stream()
                            .filter(d -> d.getVulnerabilityCount() != null && d.getVulnerabilityCount() > 0)
                            .map(DiscoveredDevice::getDisplayName)
                            .collect(Collectors.toList())
            ));
        }

        // Check for Telnet
        boolean hasTelnet = scan.getDevices().stream()
                .anyMatch(d -> d.getOpenPorts() != null && d.getOpenPorts().contains("\"port\":23"));
        if (hasTelnet) {
            recommendations.add(Map.of(
                    "severity", "CRITICAL",
                    "title", "Telnet service detected",
                    "description", "Telnet is an insecure protocol. Consider disabling it and using SSH instead.",
                    "affectedDevices", Collections.emptyList()
            ));
        }

        // Check for FTP
        boolean hasFtp = scan.getDevices().stream()
                .anyMatch(d -> d.getOpenPorts() != null && d.getOpenPorts().contains("\"port\":21"));
        if (hasFtp) {
            recommendations.add(Map.of(
                    "severity", "MEDIUM",
                    "title", "FTP service detected",
                    "description", "FTP transmits credentials in plain text. Consider using SFTP or FTPS instead.",
                    "affectedDevices", Collections.emptyList()
            ));
        }

        return Map.of(
                "score", scan.getSecurityScore() != null ? scan.getSecurityScore() : 0,
                "grade", scan.getSecurityGrade() != null ? scan.getSecurityGrade() : "N/A",
                "vulnerableDevices", scan.getVulnerableDevices() != null ? scan.getVulnerableDevices() : 0,
                "recommendations", recommendations,
                "lastUpdated", scan.getCompletedAt() != null ? scan.getCompletedAt() : scan.getCreatedAt()
        );
    }
}
