package com.orbvpn.api.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Cache configuration using Caffeine for high-performance in-memory caching.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Cache names used throughout the application.
     */
    public static final String SERVERS_CACHE = "servers";
    public static final String GROUPS_CACHE = "groups";
    public static final String ROLES_CACHE = "roles";
    public static final String SERVICE_GROUPS_CACHE = "serviceGroups";
    public static final String CONGESTION_LEVELS_CACHE = "congestionLevels";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        // Default cache configuration
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .recordStats());

        // Register cache names
        cacheManager.setCacheNames(java.util.List.of(
                SERVERS_CACHE,
                GROUPS_CACHE,
                ROLES_CACHE,
                SERVICE_GROUPS_CACHE,
                CONGESTION_LEVELS_CACHE
        ));

        return cacheManager;
    }
}
