package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.DnsUserRule;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.enums.DnsServiceType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DnsUserRuleRepository extends JpaRepository<DnsUserRule, Long> {

    List<DnsUserRule> findByUser(User user);

    List<DnsUserRule> findByUserAndServiceType(User user, DnsServiceType serviceType);

    List<DnsUserRule> findByUserAndEnabled(User user, boolean enabled);

    Optional<DnsUserRule> findByUserAndServiceIdAndServiceType(
        User user, String serviceId, DnsServiceType serviceType);

    @Query("SELECT r FROM DnsUserRule r WHERE r.user.id = :userId")
    List<DnsUserRule> findByUserId(@Param("userId") int userId);

    @Query("SELECT r FROM DnsUserRule r WHERE r.user.id = :userId AND r.enabled = true")
    List<DnsUserRule> findEnabledByUserId(@Param("userId") int userId);

    @Query("SELECT COUNT(r) FROM DnsUserRule r WHERE r.user.id = :userId AND r.enabled = true")
    int countEnabledByUserId(@Param("userId") int userId);

    @Modifying
    @Query("UPDATE DnsUserRule r SET r.enabled = :enabled WHERE r.user = :user AND r.serviceType = :serviceType")
    int updateEnabledByUserAndServiceType(
        @Param("user") User user,
        @Param("serviceType") DnsServiceType serviceType,
        @Param("enabled") boolean enabled);

    @Modifying
    @Query("DELETE FROM DnsUserRule r WHERE r.user = :user")
    void deleteAllByUser(@Param("user") User user);

    @Modifying
    @Query("DELETE FROM DnsUserRule r WHERE r.user = :user AND r.serviceId = :serviceId AND r.serviceType = :serviceType")
    void deleteByUserAndServiceIdAndServiceType(
        @Param("user") User user,
        @Param("serviceId") String serviceId,
        @Param("serviceType") DnsServiceType serviceType);

    // Stats queries
    @Query("SELECT COUNT(DISTINCT r.user.id) FROM DnsUserRule r WHERE r.enabled = true")
    int countDistinctUsersWithEnabledRules();

    @Query("SELECT r.serviceId, COUNT(r) FROM DnsUserRule r WHERE r.enabled = true GROUP BY r.serviceId ORDER BY COUNT(r) DESC")
    List<Object[]> findPopularServices();

    // Find users with DNS activity for admin overview
    @Query("SELECT u.id, u.email, u.username, COUNT(CASE WHEN r.enabled = true THEN 1 END) " +
           "FROM User u " +
           "LEFT JOIN DnsUserRule r ON r.user = u " +
           "GROUP BY u.id, u.email, u.username " +
           "HAVING COUNT(r) > 0 " +
           "ORDER BY COUNT(r) DESC")
    List<Object[]> findUsersWithDnsActivity(Pageable pageable);
}
