package com.orbvpn.api.service.scanner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbvpn.api.domain.dto.scanner.*;
import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.domain.enums.*;
import com.orbvpn.api.repository.*;
import com.orbvpn.api.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NetworkScannerService {

    private final NetworkScanRepository scanRepository;
    private final DiscoveredDeviceRepository deviceRepository;
    private final OrbMeshNodeRepository nodeRepository;
    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${orbmesh.scanner.max-scans-per-hour:5}")
    private int maxScansPerHour;

    @Value("${orbmesh.scanner.rate-limit-enabled:true}")
    private boolean rateLimitEnabled;

    /**
     * Start a network scan for the current user
     */
    @Transactional
    public NetworkScan startScan(String networkCidr, NetworkScanType scanType) {
        User user = userService.getUser();
        return startScanForUser(user, networkCidr, scanType);
    }

    /**
     * Start a network scan for a specific user
     */
    @Transactional
    public NetworkScan startScanForUser(User user, String networkCidr, NetworkScanType scanType) {
        // Rate limiting check
        if (rateLimitEnabled) {
            LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
            long recentScans = scanRepository.countScansForUserSince(user, oneHourAgo);
            if (recentScans >= maxScansPerHour) {
                throw new IllegalStateException("Rate limit exceeded. Maximum " + maxScansPerHour + " scans per hour.");
            }
        }

        // Check for active scans
        List<NetworkScan> activeScans = scanRepository.findActiveScans(user);
        if (!activeScans.isEmpty()) {
            throw new IllegalStateException("A scan is already in progress. Please wait for it to complete.");
        }

        // Validate network CIDR
        if (!isValidCIDR(networkCidr)) {
            throw new IllegalArgumentException("Invalid network CIDR format: " + networkCidr);
        }

        // Create scan record
        NetworkScan scan = NetworkScan.builder()
                .user(user)
                .networkCidr(networkCidr)
                .scanType(scanType)
                .status(NetworkScanStatus.PENDING)
                .build();

        scan = scanRepository.save(scan);
        log.info("Created network scan {} for user {} on network {}", scan.getScanId(), user.getId(), networkCidr);

        // Trigger async scan
        triggerScanAsync(scan);

        return scan;
    }

    /**
     * Trigger the actual scan on an OrbMesh node
     */
    @Async
    @Transactional
    public void triggerScanAsync(NetworkScan scan) {
        try {
            scan.setStatus(NetworkScanStatus.RUNNING);
            scan.setStartedAt(LocalDateTime.now());
            scanRepository.save(scan);

            // Find best available node for scanning
            List<OrbMeshNode> nodes = nodeRepository.findOnlineNodes(PageRequest.of(0, 1));
            if (nodes.isEmpty()) {
                throw new RuntimeException("No available scanning nodes");
            }

            OrbMeshNode node = nodes.get(0);
            scan.setServerId(node.getId());
            scanRepository.save(scan);

            // Call OrbMesh scanner API
            String scanUrl = String.format("http://%s:%d/scanner/scan",
                    node.getIpAddress(), node.getApiPort());

            Map<String, Object> scanRequest = new HashMap<>();
            scanRequest.put("userId", scan.getUser().getId());
            scanRequest.put("networkCidr", scan.getNetworkCidr());
            scanRequest.put("scanType", scan.getScanType().name().toLowerCase());
            scanRequest.put("includePorts", scan.getScanType() == NetworkScanType.DEEP);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(scanRequest, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    scanUrl, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode responseJson = objectMapper.readTree(response.getBody());
                if (responseJson.has("scanId")) {
                    // Store external scan ID for polling
                    String externalScanId = responseJson.get("scanId").asText();
                    log.info("Scan {} started on node {}, external ID: {}",
                            scan.getScanId(), node.getId(), externalScanId);

                    // Poll for results
                    pollScanResults(scan, node, externalScanId);
                }
            } else {
                throw new RuntimeException("Failed to start scan on node: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Scan {} failed: {}", scan.getScanId(), e.getMessage());
            scan.setStatus(NetworkScanStatus.FAILED);
            scan.setErrorMessage(e.getMessage());
            scan.setCompletedAt(LocalDateTime.now());
            scanRepository.save(scan);
        }
    }

    /**
     * Poll OrbMesh node for scan results
     */
    private void pollScanResults(NetworkScan scan, OrbMeshNode node, String externalScanId) {
        int maxAttempts = 60; // Max 5 minutes for scan
        int attempts = 0;
        int pollIntervalMs = 5000; // 5 seconds

        while (attempts < maxAttempts) {
            try {
                Thread.sleep(pollIntervalMs);

                String statusUrl = String.format("http://%s:%d/scanner/status?scanId=%s&userId=%d",
                        node.getIpAddress(), node.getApiPort(), externalScanId, scan.getUser().getId());

                ResponseEntity<String> response = restTemplate.getForEntity(statusUrl, String.class);

                if (response.getStatusCode().is2xxSuccessful()) {
                    JsonNode result = objectMapper.readTree(response.getBody());

                    if (result.has("scan")) {
                        JsonNode scanResult = result.get("scan");
                        String status = scanResult.has("status") ? scanResult.get("status").asText() : "";

                        if ("COMPLETED".equals(status)) {
                            processScanResults(scan, scanResult);
                            return;
                        } else if ("FAILED".equals(status)) {
                            scan.setStatus(NetworkScanStatus.FAILED);
                            scan.setErrorMessage(scanResult.has("errorMessage") ?
                                    scanResult.get("errorMessage").asText() : "Scan failed");
                            scan.setCompletedAt(LocalDateTime.now());
                            scanRepository.save(scan);
                            return;
                        }
                    }
                }

                attempts++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("Error polling scan status: {}", e.getMessage());
                attempts++;
            }
        }

        // Timeout
        scan.setStatus(NetworkScanStatus.FAILED);
        scan.setErrorMessage("Scan timeout");
        scan.setCompletedAt(LocalDateTime.now());
        scanRepository.save(scan);
    }

    /**
     * Process and store scan results
     */
    @Transactional
    public void processScanResults(NetworkScan scan, JsonNode scanResult) {
        try {
            // Parse devices
            if (scanResult.has("devices") && scanResult.get("devices").isArray()) {
                for (JsonNode deviceNode : scanResult.get("devices")) {
                    DiscoveredDevice device = DiscoveredDevice.builder()
                            .networkScan(scan)
                            .user(scan.getUser())
                            .ipAddress(getTextOrNull(deviceNode, "ipAddress"))
                            .macAddress(getTextOrNull(deviceNode, "macAddress"))
                            .hostname(getTextOrNull(deviceNode, "hostname"))
                            .vendor(getTextOrNull(deviceNode, "vendor"))
                            .deviceType(parseDeviceType(getTextOrNull(deviceNode, "deviceType")))
                            .isOnline(deviceNode.has("isOnline") && deviceNode.get("isOnline").asBoolean())
                            .responseTimeMs(deviceNode.has("responseTimeMs") ?
                                    deviceNode.get("responseTimeMs").asInt() : null)
                            .build();

                    // Parse open ports
                    if (deviceNode.has("openPorts") && deviceNode.get("openPorts").isArray()) {
                        device.setOpenPorts(deviceNode.get("openPorts").toString());
                        device.setOpenPortCount(deviceNode.get("openPorts").size());

                        // Calculate vulnerability count based on open ports
                        int vulnCount = calculateVulnerabilityCount(deviceNode.get("openPorts"));
                        device.setVulnerabilityCount(vulnCount);
                        device.setSecurityRiskLevel(calculateRiskLevel(vulnCount));
                    }

                    scan.addDevice(device);
                }
            }

            // Update scan statistics
            scan.calculateStats();
            scan.setDurationMs(scanResult.has("durationMs") ?
                    scanResult.get("durationMs").asLong() : null);

            // Calculate security score
            calculateSecurityScore(scan);

            scan.setStatus(NetworkScanStatus.COMPLETED);
            scan.setCompletedAt(LocalDateTime.now());

            scanRepository.save(scan);
            log.info("Scan {} completed: {} devices found, security score: {}",
                    scan.getScanId(), scan.getTotalDevices(), scan.getSecurityScore());

        } catch (Exception e) {
            log.error("Error processing scan results: {}", e.getMessage());
            scan.setStatus(NetworkScanStatus.FAILED);
            scan.setErrorMessage("Failed to process results: " + e.getMessage());
            scan.setCompletedAt(LocalDateTime.now());
            scanRepository.save(scan);
        }
    }

    /**
     * Calculate security score based on scan results
     */
    private void calculateSecurityScore(NetworkScan scan) {
        int score = 100;
        int deductions = 0;

        // Deduct for vulnerable devices
        int vulnerableDevices = scan.getVulnerableDevices() != null ? scan.getVulnerableDevices() : 0;
        deductions += vulnerableDevices * 10;

        // Deduct for common security issues
        for (DiscoveredDevice device : scan.getDevices()) {
            if (device.getOpenPorts() != null) {
                // Check for risky ports
                String ports = device.getOpenPorts().toLowerCase();
                if (ports.contains("23")) deductions += 15; // Telnet
                if (ports.contains("21")) deductions += 5;  // FTP
                if (ports.contains("445")) deductions += 10; // SMB
                if (ports.contains("3389")) deductions += 5; // RDP
                if (ports.contains("5900")) deductions += 5; // VNC
            }
        }

        // Check for default gateway security
        boolean hasSecureRouter = scan.getDevices().stream()
                .filter(d -> d.getIsGateway() != null && d.getIsGateway())
                .anyMatch(d -> d.getOpenPortCount() != null && d.getOpenPortCount() < 5);
        if (!hasSecureRouter && scan.getTotalDevices() > 0) {
            deductions += 10;
        }

        score = Math.max(0, Math.min(100, score - deductions));
        scan.setSecurityScore(score);
        scan.setSecurityGrade(calculateGrade(score));
    }

    private String calculateGrade(int score) {
        if (score >= 95) return "A+";
        if (score >= 90) return "A";
        if (score >= 85) return "A-";
        if (score >= 80) return "B+";
        if (score >= 75) return "B";
        if (score >= 70) return "B-";
        if (score >= 65) return "C+";
        if (score >= 60) return "C";
        if (score >= 55) return "C-";
        if (score >= 50) return "D";
        return "F";
    }

    private int calculateVulnerabilityCount(JsonNode openPorts) {
        int count = 0;
        Set<Integer> riskyPorts = Set.of(21, 23, 25, 110, 139, 445, 3389, 5900);

        for (JsonNode port : openPorts) {
            int portNum = port.has("port") ? port.get("port").asInt() : 0;
            if (riskyPorts.contains(portNum)) {
                count++;
            }
        }
        return count;
    }

    private String calculateRiskLevel(int vulnCount) {
        if (vulnCount == 0) return "LOW";
        if (vulnCount <= 2) return "MEDIUM";
        if (vulnCount <= 4) return "HIGH";
        return "CRITICAL";
    }

    private DeviceType parseDeviceType(String type) {
        if (type == null || type.isEmpty()) return DeviceType.UNKNOWN;
        try {
            return DeviceType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return DeviceType.UNKNOWN;
        }
    }

    private String getTextOrNull(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ?
                node.get(field).asText() : null;
    }

    private boolean isValidCIDR(String cidr) {
        if (cidr == null || !cidr.contains("/")) return false;
        String[] parts = cidr.split("/");
        if (parts.length != 2) return false;

        // Validate prefix length
        try {
            int prefix = Integer.parseInt(parts[1]);
            if (prefix < 16 || prefix > 30) return false; // Limit scan range
        } catch (NumberFormatException e) {
            return false;
        }

        // Validate IP address
        String ip = parts[0];
        String[] octets = ip.split("\\.");
        if (octets.length != 4) return false;

        for (String octet : octets) {
            try {
                int value = Integer.parseInt(octet);
                if (value < 0 || value > 255) return false;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        return true;
    }

    // Query methods

    /**
     * Get scan by ID
     */
    public Optional<NetworkScan> getScanById(String scanId) {
        return scanRepository.findByScanId(scanId);
    }

    /**
     * Get scan history for current user
     */
    public Page<NetworkScan> getScanHistory(int page, int size) {
        User user = userService.getUser();
        return scanRepository.findByUserOrderByCreatedAtDesc(user, PageRequest.of(page, size));
    }

    /**
     * Get latest scan for current user
     */
    public Optional<NetworkScan> getLatestScan() {
        User user = userService.getUser();
        Page<NetworkScan> scans = scanRepository.findByUserOrderByCreatedAtDesc(user, PageRequest.of(0, 1));
        return scans.hasContent() ? Optional.of(scans.getContent().get(0)) : Optional.empty();
    }

    /**
     * Get devices from a scan
     */
    public List<DiscoveredDevice> getScanDevices(String scanId) {
        NetworkScan scan = scanRepository.findByScanId(scanId)
                .orElseThrow(() -> new IllegalArgumentException("Scan not found: " + scanId));

        User user = userService.getUser();
        if (scan.getUser().getId() != user.getId()) {
            throw new IllegalStateException("Access denied");
        }

        return deviceRepository.findByNetworkScanOrderByIpAddressAsc(scan);
    }

    /**
     * Get user's discovered devices history
     */
    public Page<DiscoveredDevice> getUserDevices(int page, int size) {
        User user = userService.getUser();
        return deviceRepository.findByUserOrderByLastSeenAtDesc(user, PageRequest.of(page, size));
    }

    /**
     * Update device custom name
     */
    @Transactional
    public DiscoveredDevice updateDeviceName(Long deviceId, String customName) {
        User user = userService.getUser();
        DiscoveredDevice device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Device not found"));

        if (device.getUser().getId() != user.getId()) {
            throw new IllegalStateException("Access denied");
        }

        device.setCustomName(customName);
        return deviceRepository.save(device);
    }

    // Admin methods

    /**
     * Get all scans (admin)
     */
    public Page<NetworkScan> getAllScans(int page, int size) {
        return scanRepository.findAllScans(PageRequest.of(page, size));
    }

    /**
     * Get scanner statistics (admin)
     */
    public ScannerStatsDTO getAdminStats() {
        LocalDateTime today = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS);
        LocalDateTime thisWeek = today.minusDays(7);

        return ScannerStatsDTO.builder()
                .totalScans(scanRepository.count())
                .scansToday(scanRepository.countScansSince(today))
                .scansThisWeek(scanRepository.countScansSince(thisWeek))
                .averageSecurityScore(scanRepository.getAverageSecurityScore())
                .gradeDistribution(getGradeDistribution())
                .build();
    }

    private Map<String, Long> getGradeDistribution() {
        List<Object[]> distribution = scanRepository.getSecurityGradeDistribution();
        Map<String, Long> result = new HashMap<>();
        for (Object[] row : distribution) {
            result.put((String) row[0], ((Number) row[1]).longValue());
        }
        return result;
    }
}
