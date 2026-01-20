package com.orbvpn.api.service;

import com.orbvpn.api.repository.ConnectionStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConnectionStatsCleanupService {
    private final ConnectionStatsRepository connectionStatsRepository;

    @Scheduled(cron = "0 0 0 * * *") // Run at midnight every day
    @Transactional
    public void cleanupOldStats() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusMonths(3); // Keep 3 months of data

        try {
            int deletedCount = connectionStatsRepository.deleteByConnectionEndBefore(cutoffDate);
            log.info("Cleaned up {} old connection stats records", deletedCount);
        } catch (Exception e) {
            log.error("Error cleaning up old connection stats: {}", e.getMessage(), e);
        }
    }
}