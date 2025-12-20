package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.NetworkScan;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.enums.NetworkScanStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NetworkScanRepository extends JpaRepository<NetworkScan, Long> {

    Optional<NetworkScan> findByScanId(String scanId);

    Page<NetworkScan> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    List<NetworkScan> findByUserAndStatusOrderByCreatedAtDesc(User user, NetworkScanStatus status);

    @Query("SELECT ns FROM NetworkScan ns WHERE ns.user = :user AND ns.networkCidr = :networkCidr ORDER BY ns.createdAt DESC")
    List<NetworkScan> findByUserAndNetworkCidr(@Param("user") User user, @Param("networkCidr") String networkCidr, Pageable pageable);

    @Query("SELECT ns FROM NetworkScan ns WHERE ns.user = :user AND ns.status = 'RUNNING'")
    List<NetworkScan> findActiveScans(@Param("user") User user);

    @Query("SELECT COUNT(ns) FROM NetworkScan ns WHERE ns.user = :user AND ns.createdAt >= :since")
    long countScansForUserSince(@Param("user") User user, @Param("since") LocalDateTime since);

    // Admin queries
    @Query("SELECT ns FROM NetworkScan ns ORDER BY ns.createdAt DESC")
    Page<NetworkScan> findAllScans(Pageable pageable);

    @Query("SELECT ns FROM NetworkScan ns WHERE ns.status = :status ORDER BY ns.createdAt DESC")
    Page<NetworkScan> findByStatus(@Param("status") NetworkScanStatus status, Pageable pageable);

    @Query("SELECT COUNT(ns) FROM NetworkScan ns WHERE ns.createdAt >= :since")
    long countScansSince(@Param("since") LocalDateTime since);

    @Query("SELECT AVG(ns.securityScore) FROM NetworkScan ns WHERE ns.securityScore IS NOT NULL")
    Double getAverageSecurityScore();

    @Query("SELECT ns.securityGrade, COUNT(ns) FROM NetworkScan ns WHERE ns.securityGrade IS NOT NULL GROUP BY ns.securityGrade")
    List<Object[]> getSecurityGradeDistribution();
}
