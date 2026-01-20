package com.orbvpn.api.service;

import com.orbvpn.api.domain.entity.OrbMeshConnectionStats;
import com.orbvpn.api.domain.entity.OrbMeshServer;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.repository.OrbMeshConnectionStatsRepository;
import com.orbvpn.api.repository.OrbMeshServerRepository;
import com.orbvpn.api.repository.UserRepository;
import com.orbvpn.api.repository.UserSubscriptionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service for tracking active VPN connections.
 * This is called by OrbMesh servers when users connect/disconnect
 * to maintain accurate connection counts for multi-login enforcement.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrbMeshConnectionTrackingService {

    private final OrbMeshConnectionStatsRepository connectionStatsRepository;
    private final OrbMeshServerRepository serverRepository;
    private final UserRepository userRepository;
    private final UserSubscriptionRepository subscriptionRepository;

    /**
     * Record a new connection start.
     * Called by OrbMesh server when a user successfully establishes a VPN connection.
     *
     * @param protocol Mimicry protocol (teams, shaparak, doh, https)
     * @param vpnProtocol VPN protocol (wireguard, vless)
     * @return The connection record ID, or null if failed
     */
    @Transactional
    public Long recordConnectionStart(Integer userId, Long serverId, String sessionId,
                                       String protocol, String vpnProtocol,
                                       String clientIp, String clientPlatform) {
        try {
            // Find user and server
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found: " + userId));
            OrbMeshServer server = serverRepository.findById(serverId)
                    .orElseThrow(() -> new RuntimeException("Server not found: " + serverId));

            // Check if session already exists (prevent duplicates)
            Optional<OrbMeshConnectionStats> existing = connectionStatsRepository.findBySessionId(sessionId);
            if (existing.isPresent()) {
                log.warn("Connection session {} already exists, returning existing ID", sessionId);
                return existing.get().getId();
            }

            // Create new connection record
            OrbMeshConnectionStats connection = new OrbMeshConnectionStats();
            connection.setUser(user);
            connection.setServer(server);
            connection.setSessionId(sessionId);
            connection.setProtocol(protocol);
            connection.setVpnProtocol(vpnProtocol);
            connection.setClientIp(clientIp);
            connection.setClientPlatform(clientPlatform);
            connection.setConnectedAt(LocalDateTime.now());
            connection.setBytesSent(0L);
            connection.setBytesReceived(0L);
            connection.setDuration(0);
            // disconnectedAt is null = active connection

            connection = connectionStatsRepository.save(connection);

            log.info("Recorded connection start: user={}, server={}, session={}, protocol={}",
                    user.getEmail(), server.getName(), sessionId, protocol);

            return connection.getId();

        } catch (Exception e) {
            log.error("Failed to record connection start for session {}: {}", sessionId, e.getMessage());
            return null;
        }
    }

    /**
     * Record a connection end.
     * Called by OrbMesh server when a user disconnects from VPN.
     *
     * @return true if successfully recorded, false otherwise
     */
    @Transactional
    public boolean recordConnectionEnd(String sessionId, Long bytesSent, Long bytesReceived,
                                        String disconnectReason) {
        try {
            Optional<OrbMeshConnectionStats> optConnection = connectionStatsRepository.findBySessionId(sessionId);

            if (optConnection.isEmpty()) {
                log.warn("Connection session {} not found, cannot record end", sessionId);
                return false;
            }

            OrbMeshConnectionStats connection = optConnection.get();

            // Already disconnected?
            if (connection.getDisconnectedAt() != null) {
                log.warn("Connection session {} already disconnected", sessionId);
                return false;
            }

            // Update connection record
            connection.setDisconnectedAt(LocalDateTime.now());
            connection.setBytesSent(bytesSent != null ? bytesSent : 0L);
            connection.setBytesReceived(bytesReceived != null ? bytesReceived : 0L);
            connection.setDisconnectReason(disconnectReason);

            // Calculate duration
            if (connection.getConnectedAt() != null) {
                long seconds = java.time.Duration.between(
                        connection.getConnectedAt(),
                        connection.getDisconnectedAt()
                ).getSeconds();
                connection.setDuration((int) seconds);
            }

            connectionStatsRepository.save(connection);

            // Update subscription bandwidth usage
            User user = connection.getUser();
            UserSubscription subscription = user.getCurrentSubscription();
            if (subscription != null) {
                long totalBytes = (bytesSent != null ? bytesSent : 0L)
                        + (bytesReceived != null ? bytesReceived : 0L);
                subscription.addBandwidthUsage(totalBytes);
                subscriptionRepository.save(subscription);
                log.debug("Updated bandwidth usage for user {}: +{} bytes, total now {} bytes",
                        user.getEmail(), totalBytes, subscription.getBandwidthUsedBytes());
            }

            log.info("Recorded connection end: session={}, duration={}s, sent={}, received={}, reason={}",
                    sessionId, connection.getDuration(), bytesSent, bytesReceived, disconnectReason);

            return true;

        } catch (Exception e) {
            log.error("Failed to record connection end for session {}: {}", sessionId, e.getMessage());
            return false;
        }
    }

    /**
     * Force disconnect all connections for a user (e.g., when subscription expires).
     */
    @Transactional
    public int forceDisconnectUser(Integer userId, String reason) {
        var activeConnections = connectionStatsRepository.findActiveConnectionsByUserId(userId);

        if (activeConnections.isEmpty()) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        for (OrbMeshConnectionStats connection : activeConnections) {
            connection.setDisconnectedAt(now);
            connection.setDisconnectReason(reason);
            if (connection.getConnectedAt() != null) {
                long seconds = java.time.Duration.between(connection.getConnectedAt(), now).getSeconds();
                connection.setDuration((int) seconds);
            }
        }

        connectionStatsRepository.saveAll(activeConnections);

        log.info("Force disconnected {} connections for user {}: {}", activeConnections.size(), userId, reason);

        return activeConnections.size();
    }

    /**
     * Get count of active connections for a user.
     */
    public int getActiveConnectionCount(Integer userId) {
        return connectionStatsRepository.countActiveConnectionsByUserId(userId);
    }

    /**
     * Clean up stale connections (connections that have been active for too long without heartbeat).
     * Should be called periodically by a scheduled task.
     */
    @Transactional
    public int cleanupStaleConnections(int maxAgeMinutes) {
        // TODO: Implement stale connection cleanup
        // This would mark connections as disconnected if they haven't received
        // a heartbeat in maxAgeMinutes
        return 0;
    }
}
