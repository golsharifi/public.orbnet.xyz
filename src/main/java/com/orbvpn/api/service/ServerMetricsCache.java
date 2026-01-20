package com.orbvpn.api.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.orbvpn.api.domain.entity.Server;
import com.orbvpn.api.repository.ServerRepository;
import com.orbvpn.api.service.common.SshUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Caches server metrics (connected user counts) to avoid blocking SSH calls per request.
 * Metrics are refreshed on a schedule in the background.
 */
@Service
@Slf4j
public class ServerMetricsCache {

    private final ServerRepository serverRepository;

    // Cache: serverId -> connectedUserCount
    private final Cache<Integer, Integer> connectedUsersCache;

    // Track last successful update time per server
    private final Map<Integer, Long> lastUpdateTime = new ConcurrentHashMap<>();

    // Executor for parallel SSH calls during refresh
    private final ExecutorService executorService;

    public ServerMetricsCache(ServerRepository serverRepository) {
        this.serverRepository = serverRepository;

        this.connectedUsersCache = Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(Duration.ofMinutes(5))
                .build();

        this.executorService = Executors.newFixedThreadPool(10);
    }

    /**
     * Get cached connected user count for a server.
     * Returns 0 if not cached (will be populated on next scheduled refresh).
     */
    public int getConnectedUserCount(Server server) {
        Integer count = connectedUsersCache.getIfPresent(server.getId());
        return count != null ? count : 0;
    }

    /**
     * Get cached connected user count by server ID.
     */
    public int getConnectedUserCount(int serverId) {
        Integer count = connectedUsersCache.getIfPresent(serverId);
        return count != null ? count : 0;
    }

    /**
     * Get total connected users across all cached servers.
     */
    public int getTotalConnectedUsers() {
        return connectedUsersCache.asMap().values().stream()
                .mapToInt(Integer::intValue)
                .sum();
    }

    /**
     * Get all cached metrics as a map.
     */
    public Map<Integer, Integer> getAllMetrics() {
        return Map.copyOf(connectedUsersCache.asMap());
    }

    /**
     * Manually refresh metrics for a specific server.
     * Use sparingly - prefer the scheduled refresh.
     */
    public void refreshServerMetrics(Server server) {
        try {
            int count = SshUtil.getServerConnectedUsers(server);
            connectedUsersCache.put(server.getId(), count);
            lastUpdateTime.put(server.getId(), System.currentTimeMillis());
            log.debug("Refreshed metrics for server {}: {} users", server.getId(), count);
        } catch (Exception e) {
            log.warn("Failed to refresh metrics for server {}: {}", server.getId(), e.getMessage());
        }
    }

    /**
     * Scheduled job to refresh all server metrics in parallel.
     * Runs every 2 minutes.
     */
    @Scheduled(fixedRate = 120000, initialDelay = 10000)
    public void refreshAllServerMetrics() {
        log.info("Starting scheduled server metrics refresh");

        try {
            var servers = serverRepository.findAll().stream()
                    .filter(s -> s.getHide() == 0)
                    .toList();

            // Submit all SSH calls in parallel
            var futures = servers.stream()
                    .map(server -> executorService.submit(() -> {
                        try {
                            int count = SshUtil.getServerConnectedUsers(server);
                            connectedUsersCache.put(server.getId(), count);
                            lastUpdateTime.put(server.getId(), System.currentTimeMillis());
                            return count;
                        } catch (Exception e) {
                            log.debug("Failed to get metrics for server {}: {}", server.getId(), e.getMessage());
                            return 0;
                        }
                    }))
                    .toList();

            // Wait for all to complete (with timeout)
            int totalUsers = 0;
            for (var future : futures) {
                try {
                    totalUsers += future.get(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.debug("Timeout waiting for server metrics");
                }
            }

            log.info("Server metrics refresh completed: {} total users across {} servers",
                    totalUsers, servers.size());

        } catch (Exception e) {
            log.error("Error during server metrics refresh", e);
        }
    }

    /**
     * Check if metrics for a server are stale (older than 5 minutes).
     */
    public boolean isStale(int serverId) {
        Long lastUpdate = lastUpdateTime.get(serverId);
        if (lastUpdate == null) {
            return true;
        }
        return System.currentTimeMillis() - lastUpdate > Duration.ofMinutes(5).toMillis();
    }
}
