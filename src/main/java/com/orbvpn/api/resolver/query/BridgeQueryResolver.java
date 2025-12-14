package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.dto.BridgeConnectionStatsView;
import com.orbvpn.api.domain.dto.BridgeConnectionView;
import com.orbvpn.api.domain.dto.BridgeServerView;
import com.orbvpn.api.domain.dto.BridgeSettingsView;
import com.orbvpn.api.service.BridgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;

@Slf4j
@Controller
@RequiredArgsConstructor
public class BridgeQueryResolver {

    private final BridgeService bridgeService;

    @Secured(USER)
    @QueryMapping
    public List<BridgeServerView> getBridgeServers() {
        log.info("Fetching all bridge servers");
        try {
            List<BridgeServerView> servers = bridgeService.getBridgeServers();
            log.info("Successfully retrieved {} bridge servers", servers.size());
            return servers;
        } catch (Exception e) {
            log.error("Error fetching bridge servers - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(USER)
    @QueryMapping
    public List<BridgeServerView> getBridgeServersByCountry(@Argument String countryCode) {
        log.info("Fetching bridge servers for country: {}", countryCode);
        try {
            List<BridgeServerView> servers = bridgeService.getBridgeServersByCountry(countryCode);
            log.info("Successfully retrieved {} bridge servers for country {}", servers.size(), countryCode);
            return servers;
        } catch (Exception e) {
            log.error("Error fetching bridge servers for country {} - Error: {}", countryCode, e.getMessage(), e);
            throw e;
        }
    }

    @Secured(USER)
    @QueryMapping
    public BridgeServerView getRecommendedBridge() {
        log.info("Fetching recommended bridge server");
        try {
            BridgeServerView server = bridgeService.getRecommendedBridge();
            if (server != null) {
                log.info("Recommended bridge server: {}", server.getName());
            } else {
                log.info("No bridge server available");
            }
            return server;
        } catch (Exception e) {
            log.error("Error fetching recommended bridge - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(USER)
    @QueryMapping
    public BridgeSettingsView getBridgeSettings() {
        log.info("Fetching bridge settings");
        try {
            BridgeSettingsView settings = bridgeService.getBridgeSettings();
            log.info("Successfully retrieved bridge settings: enabled={}", settings.getEnabled());
            return settings;
        } catch (Exception e) {
            log.error("Error fetching bridge settings - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(USER)
    @QueryMapping
    public Boolean checkBridgeNeeded(@Argument Long exitServerId) {
        log.info("Checking if bridge is needed for exit server: {}", exitServerId);
        try {
            boolean needed = bridgeService.checkBridgeNeeded(exitServerId);
            log.info("Bridge needed for exit server {}: {}", exitServerId, needed);
            return needed;
        } catch (Exception e) {
            log.error("Error checking bridge needed for exit server {} - Error: {}", exitServerId, e.getMessage(), e);
            throw e;
        }
    }

    @Secured(USER)
    @QueryMapping
    public Map<String, Object> getBridgeConnectionHistory(
            @Argument Integer page,
            @Argument Integer size) {
        int pageNum = page != null ? page : 0;
        int pageSize = size != null ? size : 20;

        log.info("Fetching bridge connection history - page: {}, size: {}", pageNum, pageSize);
        try {
            Page<BridgeConnectionView> pageResult = bridgeService.getConnectionHistory(
                    PageRequest.of(pageNum, pageSize));

            Map<String, Object> result = new HashMap<>();
            result.put("content", pageResult.getContent());
            result.put("totalElements", pageResult.getTotalElements());
            result.put("totalPages", pageResult.getTotalPages());
            result.put("number", pageResult.getNumber());
            result.put("size", pageResult.getSize());

            log.info("Successfully retrieved {} bridge connections", pageResult.getTotalElements());
            return result;
        } catch (Exception e) {
            log.error("Error fetching bridge connection history - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(USER)
    @QueryMapping
    public Map<String, Object> getBridgeConnectionHistoryByDateRange(
            @Argument LocalDateTime startDate,
            @Argument LocalDateTime endDate,
            @Argument Integer page,
            @Argument Integer size) {
        int pageNum = page != null ? page : 0;
        int pageSize = size != null ? size : 20;

        log.info("Fetching bridge connection history - startDate: {}, endDate: {}, page: {}, size: {}",
                startDate, endDate, pageNum, pageSize);
        try {
            Page<BridgeConnectionView> pageResult = bridgeService.getConnectionHistoryByDateRange(
                    startDate, endDate, PageRequest.of(pageNum, pageSize));

            Map<String, Object> result = new HashMap<>();
            result.put("content", pageResult.getContent());
            result.put("totalElements", pageResult.getTotalElements());
            result.put("totalPages", pageResult.getTotalPages());
            result.put("number", pageResult.getNumber());
            result.put("size", pageResult.getSize());

            log.info("Successfully retrieved {} bridge connections for date range", pageResult.getTotalElements());
            return result;
        } catch (Exception e) {
            log.error("Error fetching bridge connection history by date range - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(USER)
    @QueryMapping
    public BridgeConnectionStatsView getBridgeConnectionStats() {
        log.info("Fetching bridge connection stats");
        try {
            BridgeConnectionStatsView stats = bridgeService.getConnectionStats();
            log.info("Successfully retrieved bridge connection stats: {} total connections",
                    stats.getTotalConnections());
            return stats;
        } catch (Exception e) {
            log.error("Error fetching bridge connection stats - Error: {}", e.getMessage(), e);
            throw e;
        }
    }
}
