package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.ClientMetadata;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for ClientMetadata entity.
 * Provides methods to query client metadata for analytics and security purposes.
 */
@Repository
public interface ClientMetadataRepository extends JpaRepository<ClientMetadata, Long> {

    /**
     * Find all metadata for a specific user
     */
    Page<ClientMetadata> findByUserIdOrderByCreatedAtDesc(Integer userId, Pageable pageable);

    /**
     * Find metadata by event type
     */
    Page<ClientMetadata> findByEventTypeOrderByCreatedAtDesc(String eventType, Pageable pageable);

    /**
     * Find metadata by user and event type
     */
    List<ClientMetadata> findByUserIdAndEventTypeOrderByCreatedAtDesc(Integer userId, String eventType);

    /**
     * Find the most recent signup metadata for a user
     */
    @Query("SELECT cm FROM ClientMetadata cm WHERE cm.user.id = :userId AND cm.eventType = 'SIGNUP' ORDER BY cm.createdAt DESC")
    List<ClientMetadata> findSignupMetadataByUser(@Param("userId") Integer userId);

    /**
     * Find metadata by country code
     */
    Page<ClientMetadata> findByCountryCodeOrderByCreatedAtDesc(String countryCode, Pageable pageable);

    /**
     * Find metadata by platform
     */
    Page<ClientMetadata> findByPlatformOrderByCreatedAtDesc(String platform, Pageable pageable);

    /**
     * Find metadata within a date range
     */
    Page<ClientMetadata> findByCreatedAtBetweenOrderByCreatedAtDesc(
            LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    /**
     * Count signups by country within date range (for analytics)
     */
    @Query("SELECT cm.countryCode, cm.countryName, COUNT(cm) FROM ClientMetadata cm " +
           "WHERE cm.eventType = 'SIGNUP' AND cm.createdAt >= :startDate AND cm.createdAt <= :endDate " +
           "GROUP BY cm.countryCode, cm.countryName ORDER BY COUNT(cm) DESC")
    List<Object[]> countSignupsByCountry(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Count signups by platform within date range
     */
    @Query("SELECT cm.platform, COUNT(cm) FROM ClientMetadata cm " +
           "WHERE cm.eventType = 'SIGNUP' AND cm.createdAt >= :startDate AND cm.createdAt <= :endDate " +
           "GROUP BY cm.platform ORDER BY COUNT(cm) DESC")
    List<Object[]> countSignupsByPlatform(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Count signups by OS within date range
     */
    @Query("SELECT cm.osName, COUNT(cm) FROM ClientMetadata cm " +
           "WHERE cm.eventType = 'SIGNUP' AND cm.createdAt >= :startDate AND cm.createdAt <= :endDate " +
           "GROUP BY cm.osName ORDER BY COUNT(cm) DESC")
    List<Object[]> countSignupsByOS(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Count events by type within date range
     */
    @Query("SELECT cm.eventType, COUNT(cm) FROM ClientMetadata cm " +
           "WHERE cm.createdAt >= :startDate AND cm.createdAt <= :endDate " +
           "GROUP BY cm.eventType ORDER BY COUNT(cm) DESC")
    List<Object[]> countEventsByType(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Count signups by browser within date range (for web analytics)
     */
    @Query("SELECT cm.browserName, COUNT(cm) FROM ClientMetadata cm " +
           "WHERE cm.eventType = 'SIGNUP' AND cm.platform = 'WEB' " +
           "AND cm.createdAt >= :startDate AND cm.createdAt <= :endDate " +
           "GROUP BY cm.browserName ORDER BY COUNT(cm) DESC")
    List<Object[]> countSignupsByBrowser(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Count signups by language within date range
     */
    @Query("SELECT cm.language, COUNT(cm) FROM ClientMetadata cm " +
           "WHERE cm.eventType = 'SIGNUP' AND cm.createdAt >= :startDate AND cm.createdAt <= :endDate " +
           "GROUP BY cm.language ORDER BY COUNT(cm) DESC")
    List<Object[]> countSignupsByLanguage(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Count signups by UTM source within date range (marketing attribution)
     */
    @Query("SELECT cm.utmSource, COUNT(cm) FROM ClientMetadata cm " +
           "WHERE cm.eventType = 'SIGNUP' AND cm.utmSource IS NOT NULL " +
           "AND cm.createdAt >= :startDate AND cm.createdAt <= :endDate " +
           "GROUP BY cm.utmSource ORDER BY COUNT(cm) DESC")
    List<Object[]> countSignupsByUtmSource(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Search metadata with multiple filters
     */
    @Query("SELECT cm FROM ClientMetadata cm WHERE " +
           "(:userId IS NULL OR cm.user.id = :userId) AND " +
           "(:eventType IS NULL OR cm.eventType = :eventType) AND " +
           "(:platform IS NULL OR cm.platform = :platform) AND " +
           "(:countryCode IS NULL OR cm.countryCode = :countryCode) AND " +
           "(:startDate IS NULL OR cm.createdAt >= :startDate) AND " +
           "(:endDate IS NULL OR cm.createdAt <= :endDate) " +
           "ORDER BY cm.createdAt DESC")
    Page<ClientMetadata> searchMetadata(
            @Param("userId") Integer userId,
            @Param("eventType") String eventType,
            @Param("platform") String platform,
            @Param("countryCode") String countryCode,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    /**
     * Find recent logins from different IP addresses (security - detect account sharing)
     */
    @Query("SELECT DISTINCT cm.ipAddress FROM ClientMetadata cm " +
           "WHERE cm.user.id = :userId AND cm.eventType = 'LOGIN' " +
           "AND cm.createdAt >= :since ORDER BY cm.createdAt DESC")
    List<String> findRecentLoginIpsByUser(@Param("userId") Integer userId, @Param("since") LocalDateTime since);

    /**
     * Find recent logins from different countries (security)
     */
    @Query("SELECT DISTINCT cm.countryCode FROM ClientMetadata cm " +
           "WHERE cm.user.id = :userId AND cm.eventType = 'LOGIN' " +
           "AND cm.createdAt >= :since")
    List<String> findRecentLoginCountriesByUser(@Param("userId") Integer userId, @Param("since") LocalDateTime since);

    /**
     * Find recent device fingerprints for a user (platform + osName + deviceModel + browserName)
     * Used for new device detection
     */
    @Query("SELECT DISTINCT CONCAT(COALESCE(cm.platform, ''), '|', COALESCE(cm.osName, ''), '|', " +
           "COALESCE(cm.deviceModel, ''), '|', COALESCE(cm.browserName, '')) " +
           "FROM ClientMetadata cm " +
           "WHERE cm.user.id = :userId AND cm.eventType IN ('LOGIN', 'SIGNUP') " +
           "AND cm.createdAt >= :since")
    List<String> findRecentDeviceFingerprints(@Param("userId") Integer userId, @Param("since") LocalDateTime since);

    /**
     * Find all unique device fingerprints ever used by a user
     */
    @Query("SELECT DISTINCT CONCAT(COALESCE(cm.platform, ''), '|', COALESCE(cm.osName, ''), '|', " +
           "COALESCE(cm.deviceModel, ''), '|', COALESCE(cm.browserName, '')) " +
           "FROM ClientMetadata cm " +
           "WHERE cm.user.id = :userId AND cm.eventType IN ('LOGIN', 'SIGNUP')")
    List<String> findAllDeviceFingerprintsForUser(@Param("userId") Integer userId);

    /**
     * Check if user has ever logged in from this country
     */
    @Query("SELECT COUNT(cm) > 0 FROM ClientMetadata cm " +
           "WHERE cm.user.id = :userId AND cm.countryCode = :countryCode " +
           "AND cm.eventType IN ('LOGIN', 'SIGNUP')")
    boolean hasUserEverLoggedFromCountry(@Param("userId") Integer userId, @Param("countryCode") String countryCode);

    /**
     * Get the most recent login metadata for a user
     */
    @Query("SELECT cm FROM ClientMetadata cm WHERE cm.user.id = :userId " +
           "AND cm.eventType = 'LOGIN' ORDER BY cm.createdAt DESC")
    List<ClientMetadata> findMostRecentLogins(@Param("userId") Integer userId, Pageable pageable);

    /**
     * Count logins from a specific device fingerprint
     */
    @Query("SELECT COUNT(cm) FROM ClientMetadata cm " +
           "WHERE cm.user.id = :userId AND cm.eventType IN ('LOGIN', 'SIGNUP') " +
           "AND CONCAT(COALESCE(cm.platform, ''), '|', COALESCE(cm.osName, ''), '|', " +
           "COALESCE(cm.deviceModel, ''), '|', COALESCE(cm.browserName, '')) = :fingerprint")
    long countLoginsFromDeviceFingerprint(@Param("userId") Integer userId, @Param("fingerprint") String fingerprint);
}
