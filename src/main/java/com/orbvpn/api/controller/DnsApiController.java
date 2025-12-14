package com.orbvpn.api.controller;

import com.orbvpn.api.domain.dto.DnsUserRuleView;
import com.orbvpn.api.domain.dto.DnsWhitelistedIpView;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.enums.DnsServiceType;
import com.orbvpn.api.repository.UserRepository;
import com.orbvpn.api.service.DnsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API Controller for DNS service.
 * These endpoints are called by the Go DNS server (OrbMESH) to fetch and sync user rules.
 * Authentication is via X-API-Key header.
 */
@RestController
@RequestMapping("/api/dns")
@RequiredArgsConstructor
@Slf4j
public class DnsApiController {

    private final DnsService dnsService;
    private final UserRepository userRepository;

    @Value("${orbmesh.api-key:}")
    private String orbmeshApiKey;

    /**
     * Get user DNS rules - called by Go DNS server.
     * Returns rules in the format expected by the Go server.
     */
    @GetMapping("/user-rules/{userId}")
    public ResponseEntity<?> getUserRules(
            @PathVariable Integer userId,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        if (!validateApiKey(apiKey)) {
            log.warn("Invalid API key for DNS user-rules request, userId={}", userId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid API key");
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        List<DnsUserRuleView> rules = dnsService.getUserRules(user);

        // Convert to format expected by Go server
        Map<String, Object> response = new HashMap<>();
        response.put("userId", String.valueOf(userId));
        response.put("dnsEnabled", true); // DNS is enabled if user has any rules or by default

        Map<String, Map<String, Object>> serviceRules = new HashMap<>();
        for (DnsUserRuleView rule : rules) {
            Map<String, Object> ruleData = new HashMap<>();
            ruleData.put("serviceId", rule.getServiceId());
            ruleData.put("enabled", rule.isEnabled());
            ruleData.put("region", rule.getPreferredRegion() != null ? rule.getPreferredRegion() : "US");
            serviceRules.put(rule.getServiceId(), ruleData);
        }
        response.put("serviceRules", serviceRules);
        response.put("lastUpdated", System.currentTimeMillis());

        log.debug("Returning {} DNS rules for user {}", rules.size(), userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get all whitelisted IPs - called by Go DNS server.
     * Returns list of IP -> userId mappings for whitelist authentication.
     */
    @GetMapping("/whitelist")
    public ResponseEntity<?> getAllWhitelistedIps(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        if (!validateApiKey(apiKey)) {
            log.warn("Invalid API key for DNS whitelist request");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid API key");
        }

        // Get all active whitelisted IPs across all users
        List<Map<String, String>> entries = dnsService.getAllActiveWhitelistedIps().stream()
                .map(ip -> {
                    Map<String, String> entry = new HashMap<>();
                    entry.put("ip", ip.getIpAddress());
                    entry.put("userId", String.valueOf(ip.getUserId()));
                    return entry;
                })
                .collect(Collectors.toList());

        log.debug("Returning {} whitelist entries", entries.size());
        return ResponseEntity.ok(entries);
    }

    /**
     * Get user's whitelisted IPs - called by Go DNS server.
     */
    @GetMapping("/whitelist/{userId}")
    public ResponseEntity<?> getUserWhitelistedIps(
            @PathVariable Integer userId,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        if (!validateApiKey(apiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid API key");
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        List<DnsWhitelistedIpView> ips = dnsService.getWhitelistedIps(user);
        return ResponseEntity.ok(ips);
    }

    /**
     * Notify Java backend of rule change from Go server.
     * This is called when a user changes rules via the Go server's API.
     */
    @PostMapping("/sync-rule")
    public ResponseEntity<?> syncRuleFromGoServer(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        if (!validateApiKey(apiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid API key");
        }

        try {
            Integer userId = Integer.parseInt(payload.get("userId").toString());
            String serviceId = payload.get("serviceId").toString();
            boolean enabled = Boolean.parseBoolean(payload.get("enabled").toString());
            String region = payload.containsKey("region") ? payload.get("region").toString() : null;
            String serviceTypeStr = payload.containsKey("serviceType") ?
                payload.get("serviceType").toString() : "STREAMING";

            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                return ResponseEntity.notFound().build();
            }

            // Update rule in database (without re-syncing to Go server)
            dnsService.setUserRuleFromGoServer(user, serviceId,
                DnsServiceType.valueOf(serviceTypeStr), enabled, region);

            log.info("Synced DNS rule from Go server: user={}, service={}, enabled={}",
                userId, serviceId, enabled);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to sync rule from Go server: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("Failed to sync rule: " + e.getMessage());
        }
    }

    /**
     * Get DNS servers list for Go server to know regional routing targets.
     */
    @GetMapping("/servers")
    public ResponseEntity<?> getDnsServers(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        if (!validateApiKey(apiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid API key");
        }

        return ResponseEntity.ok(dnsService.getRegionalServers());
    }

    /**
     * Health check endpoint for monitoring.
     */
    @GetMapping("/health")
    public ResponseEntity<?> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(health);
    }

    private boolean validateApiKey(String apiKey) {
        if (orbmeshApiKey == null || orbmeshApiKey.isEmpty()) {
            // If no API key configured, allow all requests (development mode)
            log.warn("No OrbMesh API key configured - allowing unauthenticated DNS API access");
            return true;
        }
        return orbmeshApiKey.equals(apiKey);
    }
}
