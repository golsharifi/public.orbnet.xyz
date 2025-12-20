package com.orbvpn.api.service;

import com.orbvpn.api.domain.dto.BridgeConnectionStatsView;
import com.orbvpn.api.domain.dto.BridgeConnectionView;
import com.orbvpn.api.domain.dto.BridgeServerView;
import com.orbvpn.api.domain.dto.BridgeSettingsView;
import com.orbvpn.api.domain.entity.BridgeConnectionLog;
import com.orbvpn.api.domain.entity.OrbMeshServer;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserBridgeSettings;
import com.orbvpn.api.exception.NotFoundException;
import com.orbvpn.api.repository.BridgeConnectionLogRepository;
import com.orbvpn.api.repository.OrbMeshServerRepository;
import com.orbvpn.api.repository.UserBridgeSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BridgeService {

    private final OrbMeshServerRepository orbMeshServerRepository;
    private final UserBridgeSettingsRepository userBridgeSettingsRepository;
    private final BridgeConnectionLogRepository bridgeConnectionLogRepository;
    private final UserService userService;

    /**
     * Get all available bridge servers (from OrbMesh servers that are online)
     */
    public List<BridgeServerView> getBridgeServers() {
        log.info("Fetching all bridge servers from OrbMesh servers");
        // For now, return all online OrbMesh servers as potential bridge servers
        List<OrbMeshServer> servers = orbMeshServerRepository.findByOnlineTrueAndEnabledTrue();
        return servers.stream()
                .map(this::toServerView)
                .collect(Collectors.toList());
    }

    /**
     * Get bridge servers by country
     */
    public List<BridgeServerView> getBridgeServersByCountry(String countryCode) {
        log.info("Fetching bridge servers for country: {}", countryCode);
        List<OrbMeshServer> servers = orbMeshServerRepository.findByCountryAndOnlineTrueAndEnabledTrue(countryCode);
        return servers.stream()
                .map(this::toServerView)
                .collect(Collectors.toList());
    }

    /**
     * Get recommended bridge based on user's location and server load
     */
    public BridgeServerView getRecommendedBridge() {
        log.info("Finding recommended bridge server");
        User user = userService.getUser();

        // Get all online OrbMesh servers
        List<OrbMeshServer> servers = orbMeshServerRepository.findByOnlineTrueAndEnabledTrue();

        if (servers.isEmpty()) {
            log.warn("No bridge servers available");
            return null;
        }

        // Return the first available server (could be enhanced with latency/load logic)
        OrbMeshServer recommended = servers.get(0);
        log.info("Recommended bridge server: {} ({})", recommended.getName(), recommended.getCountry());

        return toServerView(recommended);
    }

    /**
     * Get current user's bridge settings
     */
    public BridgeSettingsView getBridgeSettings() {
        User user = userService.getUser();
        log.info("Fetching bridge settings for user: {}", user.getId());

        return userBridgeSettingsRepository.findByUserId((long) user.getId())
                .map(this::toSettingsView)
                .orElse(BridgeSettingsView.builder()
                        .enabled(false)
                        .autoSelect(true)
                        .selectedBridgeId(null)
                        .lastUsedBridgeId(null)
                        .build());
    }

    /**
     * Enable or disable bridge for current user
     */
    public BridgeSettingsView setBridgeEnabled(boolean enabled) {
        User user = userService.getUser();
        Long userId = (long) user.getId();
        log.info("Setting bridge enabled={} for user: {}", enabled, userId);

        // Atomic upsert - creates record if not exists, updates if exists
        userBridgeSettingsRepository.upsertEnabled(userId, enabled);

        // Return fresh settings
        return getBridgeSettings();
    }

    /**
     * Select a specific bridge server
     */
    public BridgeSettingsView selectBridge(Long bridgeId) {
        User user = userService.getUser();
        Long userId = (long) user.getId();
        log.info("Selecting bridge {} for user: {}", bridgeId, userId);

        if (bridgeId != null) {
            // Verify the bridge exists and is online
            OrbMeshServer bridge = orbMeshServerRepository.findById(bridgeId)
                    .orElseThrow(() -> new NotFoundException(OrbMeshServer.class, bridgeId));

            if (!Boolean.TRUE.equals(bridge.getOnline())) {
                throw new IllegalArgumentException("Server " + bridgeId + " is not online");
            }
        }

        // Atomic upsert - creates record if not exists, updates if exists
        boolean autoSelect = (bridgeId == null);
        userBridgeSettingsRepository.upsertSelectedBridge(userId, bridgeId, autoSelect);

        // Return fresh settings
        return getBridgeSettings();
    }

    /**
     * Set auto-select mode for bridge
     */
    public BridgeSettingsView setAutoBridge(boolean autoSelect) {
        User user = userService.getUser();
        Long userId = (long) user.getId();
        log.info("Setting auto bridge={} for user: {}", autoSelect, userId);

        // Atomic upsert - creates record if not exists, updates if exists
        userBridgeSettingsRepository.upsertAutoSelect(userId, autoSelect);

        // Return fresh settings
        return getBridgeSettings();
    }

    /**
     * Check if bridge is needed for connecting to a specific exit server
     * This is based on the exit server's location and user's current connection status
     */
    public boolean checkBridgeNeeded(Long exitServerId) {
        User user = userService.getUser();
        log.info("Checking if bridge needed for exit server {} and user {}", exitServerId, user.getId());

        // Check if user has bridge enabled
        BridgeSettingsView settings = getBridgeSettings();
        if (!settings.getEnabled()) {
            return false;
        }

        // Additional logic could check:
        // - User's geographic location
        // - Known censorship patterns
        // - Connection history/failures
        // For now, simply return the user's bridge enabled preference
        return true;
    }

    /**
     * Log a bridge connection
     */
    public BridgeConnectionView logBridgeConnection(Long bridgeServerId, Long exitServerId, String protocol) {
        User user = userService.getUser();
        Long userId = (long) user.getId();
        log.info("Logging bridge connection: user={}, bridge={}, exit={}, protocol={}",
                userId, bridgeServerId, exitServerId, protocol);

        BridgeConnectionLog connectionLog = new BridgeConnectionLog(user, bridgeServerId, exitServerId, protocol);
        bridgeConnectionLogRepository.save(connectionLog);

        // Update last used bridge in settings using atomic upsert
        userBridgeSettingsRepository.upsertLastUsedBridge(userId, bridgeServerId);

        return toConnectionView(connectionLog);
    }

    /**
     * Log bridge disconnection
     */
    public BridgeConnectionView logBridgeDisconnection(Long connectionId, Long bytesSent, Long bytesReceived) {
        log.info("Logging bridge disconnection: connectionId={}", connectionId);

        BridgeConnectionLog connectionLog = bridgeConnectionLogRepository.findById(connectionId)
                .orElseThrow(() -> new NotFoundException(BridgeConnectionLog.class, connectionId));

        connectionLog.disconnect();
        connectionLog.setBytesSent(bytesSent != null ? bytesSent : 0L);
        connectionLog.setBytesReceived(bytesReceived != null ? bytesReceived : 0L);
        bridgeConnectionLogRepository.save(connectionLog);

        return toConnectionView(connectionLog);
    }

    /**
     * Get connection history for current user
     */
    public Page<BridgeConnectionView> getConnectionHistory(Pageable pageable) {
        User user = userService.getUser();
        log.info("Fetching bridge connection history for user: {}", user.getId());

        return bridgeConnectionLogRepository.findByUserIdOrderByConnectedAtDesc((long) user.getId(), pageable)
                .map(this::toConnectionView);
    }

    /**
     * Get connection history for a specific date range
     */
    public Page<BridgeConnectionView> getConnectionHistoryByDateRange(
            LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        User user = userService.getUser();
        log.info("Fetching bridge connection history for user {} from {} to {}",
                user.getId(), startDate, endDate);

        return bridgeConnectionLogRepository.findByUserIdAndDateRange(
                (long) user.getId(), startDate, endDate, pageable)
                .map(this::toConnectionView);
    }

    /**
     * Get connection stats for current user
     */
    public BridgeConnectionStatsView getConnectionStats() {
        User user = userService.getUser();
        log.info("Fetching bridge connection stats for user: {}", user.getId());

        Long totalConnections = bridgeConnectionLogRepository.countByUserId((long) user.getId());
        Long totalBytesSent = bridgeConnectionLogRepository.sumBytesSentByUserId((long) user.getId());
        Long totalBytesReceived = bridgeConnectionLogRepository.sumBytesReceivedByUserId((long) user.getId());

        return BridgeConnectionStatsView.builder()
                .totalConnections(totalConnections)
                .totalBytesSent(totalBytesSent != null ? totalBytesSent : 0L)
                .totalBytesReceived(totalBytesReceived != null ? totalBytesReceived : 0L)
                .build();
    }

    private BridgeServerView toServerView(OrbMeshServer server) {
        return BridgeServerView.builder()
                .id(server.getId())
                .name(server.getName())
                .location(server.getLocation())
                .country(server.getCountry())
                .countryCode(server.getCountryCode() != null ? server.getCountryCode() : server.getCountry())
                .ipAddress(server.getIpAddress())
                .port(server.getPort())
                .protocols(server.getProtocolsList())
                .online(server.getOnline() != null && server.getOnline())
                .load(server.getCurrentConnections() != null && server.getMaxConnections() != null && server.getMaxConnections() > 0
                        ? (float) server.getCurrentConnections() / server.getMaxConnections()
                        : 0.0f)
                .latencyMs(server.getLatencyMs())
                .priority(1) // Default priority
                .maxSessions(server.getMaxConnections())
                .build();
    }

    private BridgeSettingsView toSettingsView(UserBridgeSettings settings) {
        return BridgeSettingsView.builder()
                .enabled(settings.getEnabled())
                .selectedBridgeId(settings.getSelectedBridgeId())
                .autoSelect(settings.getAutoSelect())
                .lastUsedBridgeId(settings.getLastUsedBridgeId())
                .build();
    }

    private BridgeConnectionView toConnectionView(BridgeConnectionLog log) {
        return BridgeConnectionView.builder()
                .bridgeServerId(log.getBridgeServerId())
                .exitServerId(log.getExitServerId())
                .protocol(log.getProtocol())
                .status(log.getStatus())
                .connectedAt(log.getConnectedAt() != null
                        ? log.getConnectedAt().atOffset(java.time.ZoneOffset.UTC) : null)
                .disconnectedAt(log.getDisconnectedAt() != null
                        ? log.getDisconnectedAt().atOffset(java.time.ZoneOffset.UTC) : null)
                .bytesSent(log.getBytesSent())
                .bytesReceived(log.getBytesReceived())
                .sessionDurationSeconds(log.getSessionDurationSeconds())
                .build();
    }
}
