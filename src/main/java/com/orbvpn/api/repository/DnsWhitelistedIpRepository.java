package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.DnsWhitelistedIp;
import com.orbvpn.api.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DnsWhitelistedIpRepository extends JpaRepository<DnsWhitelistedIp, Long> {

    List<DnsWhitelistedIp> findByUser(User user);

    List<DnsWhitelistedIp> findByUserAndActive(User user, boolean active);

    Optional<DnsWhitelistedIp> findByUserAndIpAddress(User user, String ipAddress);

    Optional<DnsWhitelistedIp> findByIdAndUser(Long id, User user);

    @Query("SELECT w FROM DnsWhitelistedIp w WHERE w.user.id = :userId")
    List<DnsWhitelistedIp> findByUserId(@Param("userId") int userId);

    @Query("SELECT w FROM DnsWhitelistedIp w WHERE w.user.id = :userId AND w.active = true AND (w.expiresAt IS NULL OR w.expiresAt > :now)")
    List<DnsWhitelistedIp> findValidByUserId(@Param("userId") int userId, @Param("now") LocalDateTime now);

    @Query("SELECT COUNT(w) FROM DnsWhitelistedIp w WHERE w.user.id = :userId AND w.active = true")
    int countActiveByUserId(@Param("userId") int userId);

    @Query("SELECT w FROM DnsWhitelistedIp w WHERE w.ipAddress = :ipAddress AND w.active = true AND (w.expiresAt IS NULL OR w.expiresAt > :now)")
    List<DnsWhitelistedIp> findValidByIpAddress(@Param("ipAddress") String ipAddress, @Param("now") LocalDateTime now);

    @Query("SELECT DISTINCT w.user.id FROM DnsWhitelistedIp w WHERE w.ipAddress = :ipAddress AND w.active = true AND (w.expiresAt IS NULL OR w.expiresAt > :now)")
    List<Integer> findUserIdsByIpAddress(@Param("ipAddress") String ipAddress, @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE DnsWhitelistedIp w SET w.lastUsed = :lastUsed WHERE w.ipAddress = :ipAddress AND w.active = true")
    void updateLastUsedByIpAddress(@Param("ipAddress") String ipAddress, @Param("lastUsed") LocalDateTime lastUsed);

    @Modifying
    @Query("DELETE FROM DnsWhitelistedIp w WHERE w.user = :user")
    void deleteAllByUser(@Param("user") User user);

    @Modifying
    @Query("DELETE FROM DnsWhitelistedIp w WHERE w.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);

    // Stats queries
    @Query("SELECT COUNT(DISTINCT w.user.id) FROM DnsWhitelistedIp w WHERE w.active = true")
    int countDistinctUsersWithWhitelistedIps();

    @Query("SELECT COUNT(w) FROM DnsWhitelistedIp w WHERE w.active = true")
    long countActiveWhitelistedIps();

    /**
     * Find all active (non-expired) whitelisted IPs across all users.
     * Used by Go DNS server to build its whitelist.
     */
    @Query("SELECT w FROM DnsWhitelistedIp w WHERE w.active = true AND (w.expiresAt IS NULL OR w.expiresAt > CURRENT_TIMESTAMP)")
    List<DnsWhitelistedIp> findAllActive();
}
