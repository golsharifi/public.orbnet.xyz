package com.orbvpn.api.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.orbvpn.api.domain.dto.HistoricalStatsView;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.Duration;

@Component
public class ConnectionStatsCache {
    private final Cache<String, HistoricalStatsView> historicalStatsCache;
    private final Cache<String, byte[]> exportCache;

    public ConnectionStatsCache() {
        historicalStatsCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(Duration.ofMinutes(15))
                .build();

        exportCache = Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(Duration.ofMinutes(5))
                .build();
    }

    public String generateHistoricalStatsCacheKey(Integer userId, String period, LocalDateTime from, LocalDateTime to) {
        return String.format("historical_%d_%s_%s_%s",
                userId,
                period,
                from.toString(),
                to.toString());
    }

    public String generateExportCacheKey(String type, LocalDateTime from, LocalDateTime to, Integer userId) {
        return String.format("export_%s_%s_%s_%d",
                type,
                from.toString(),
                to.toString(),
                userId != null ? userId : 0);
    }

    public HistoricalStatsView getHistoricalStats(String key) {
        return historicalStatsCache.getIfPresent(key);
    }

    public void putHistoricalStats(String key, HistoricalStatsView value) {
        historicalStatsCache.put(key, value);
    }

    public byte[] getExportData(String key) {
        return exportCache.getIfPresent(key);
    }

    public void putExportData(String key, byte[] value) {
        exportCache.put(key, value);
    }

    public void clearCache() {
        historicalStatsCache.invalidateAll();
        exportCache.invalidateAll();
    }
}