package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.scanner.*;
import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.domain.enums.*;
import com.orbvpn.api.service.UserService;
import com.orbvpn.api.service.scanner.NetworkScannerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;

import java.util.Map;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;
import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ScannerMutationResolver {

    private final NetworkScannerService scannerService;
    private final UserService userService;

    // ========== USER MUTATIONS ==========

    @Secured(USER)
    @MutationMapping
    public Map<String, Object> startNetworkScan(@Argument Map<String, Object> input) {
        String networkCidr = (String) input.get("networkCidr");
        String scanTypeStr = (String) input.getOrDefault("scanType", "NORMAL");
        NetworkScanType scanType = NetworkScanType.valueOf(scanTypeStr.toUpperCase());

        log.info("Starting network scan on {} with type {}", networkCidr, scanType);
        try {
            NetworkScan scan = scannerService.startScan(networkCidr, scanType);
            return Map.of(
                    "success", true,
                    "message", "Network scan started successfully",
                    "scan", scan
            );
        } catch (Exception e) {
            log.error("Error starting network scan: {}", e.getMessage(), e);
            return Map.of(
                    "success", false,
                    "message", e.getMessage()
            );
        }
    }

    @Secured(USER)
    @MutationMapping
    public Map<String, Object> cancelNetworkScan(@Argument String scanId) {
        log.info("Cancelling network scan: {}", scanId);
        try {
            // TODO: Implement cancel logic
            return Map.of(
                    "success", true,
                    "message", "Scan cancellation requested"
            );
        } catch (Exception e) {
            log.error("Error cancelling scan: {}", e.getMessage(), e);
            return Map.of(
                    "success", false,
                    "message", e.getMessage()
            );
        }
    }

    @Secured(USER)
    @MutationMapping
    public Map<String, Object> updateDevice(@Argument Map<String, Object> input) {
        Long deviceId = Long.parseLong(input.get("deviceId").toString());
        String customName = (String) input.get("customName");

        log.info("Updating device {} with name: {}", deviceId, customName);
        try {
            DiscoveredDevice device = scannerService.updateDeviceName(deviceId, customName);
            return Map.of(
                    "success", true,
                    "message", "Device updated successfully",
                    "device", device
            );
        } catch (Exception e) {
            log.error("Error updating device: {}", e.getMessage(), e);
            return Map.of(
                    "success", false,
                    "message", e.getMessage()
            );
        }
    }

    // ========== ADMIN MUTATIONS ==========

    @Secured(ADMIN)
    @MutationMapping
    public Map<String, Object> adminCancelScan(@Argument String scanId) {
        log.info("Admin cancelling scan: {}", scanId);
        try {
            // TODO: Implement admin cancel logic
            return Map.of(
                    "success", true,
                    "message", "Scan cancelled by admin"
            );
        } catch (Exception e) {
            log.error("Admin error cancelling scan: {}", e.getMessage(), e);
            return Map.of(
                    "success", false,
                    "message", e.getMessage()
            );
        }
    }
}
