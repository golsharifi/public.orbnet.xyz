// src/main/java/com/orbvpn/api/service/OrbXUsageService.java
package com.orbvpn.api.service;

import com.orbvpn.api.domain.dto.OrbXUsageInput;
import com.orbvpn.api.domain.entity.OrbXConnectionStats;
import com.orbvpn.api.domain.entity.OrbXServer;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.exception.NotFoundException;
import com.orbvpn.api.repository.OrbXConnectionStatsRepository;
import com.orbvpn.api.repository.OrbXServerRepository;
import com.orbvpn.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrbXUsageService {

        private final OrbXConnectionStatsRepository statsRepository;
        private final UserRepository userRepository;
        private final OrbXServerRepository serverRepository;

        @Transactional
        public void recordUsage(OrbXUsageInput input) {
                log.info("Recording OrbX usage - User: {}, Session: {}, Protocol: {}",
                                input.getUserId(), input.getSessionId(), input.getProtocol());

                // Validate user
                User user = userRepository.findById(input.getUserId())
                                .orElseThrow(() -> new NotFoundException("User not found: " + input.getUserId()));

                // Validate server
                OrbXServer server = serverRepository.findById(input.getServerId())
                                .orElseThrow(() -> new NotFoundException(
                                                "OrbX server not found: " + input.getServerId()));

                // Create or update connection stats
                OrbXConnectionStats stats = statsRepository.findBySessionId(input.getSessionId())
                                .orElse(new OrbXConnectionStats());

                stats.setUser(user);
                stats.setServer(server);
                stats.setSessionId(input.getSessionId());

                // ✅ FIX: Map bytesSent and bytesReceived correctly
                stats.setBytesSent(input.getBytesSent() != null ? input.getBytesSent() : 0L);
                stats.setBytesReceived(input.getBytesReceived() != null ? input.getBytesReceived() : 0L);
                stats.setDuration(input.getDurationSeconds() != null ? input.getDurationSeconds() : 0);
                stats.setProtocol(input.getProtocol());

                // ✅ FIX: Handle null disconnectedAt
                LocalDateTime disconnectedAt = input.getDisconnectedAt() != null
                                ? input.getDisconnectedAt()
                                : LocalDateTime.now();

                stats.setDisconnectedAt(disconnectedAt);
                stats.setConnectedAt(disconnectedAt.minusSeconds(stats.getDuration()));

                statsRepository.save(stats);

                // Update server connection count (decrement on disconnect)
                if (server.getCurrentConnections() != null && server.getCurrentConnections() > 0) {
                        server.setCurrentConnections(server.getCurrentConnections() - 1);
                        serverRepository.save(server);
                }

                log.info("✅ OrbX usage recorded - Session: {}, Sent: {} MB, Received: {} MB",
                                input.getSessionId(),
                                stats.getBytesSent() / 1024.0 / 1024.0,
                                stats.getBytesReceived() / 1024.0 / 1024.0);
        }

        @Transactional(readOnly = true)
        public Long getTotalBandwidth(Integer userId, LocalDateTime from, LocalDateTime to) {
                return statsRepository.sumBandwidthByUserAndDateRange(userId, from, to);
        }

        @Transactional(readOnly = true)
        public Integer getTotalSessions(Integer userId, LocalDateTime from, LocalDateTime to) {
                return statsRepository.countSessionsByUserAndDateRange(userId, from, to);
        }
}