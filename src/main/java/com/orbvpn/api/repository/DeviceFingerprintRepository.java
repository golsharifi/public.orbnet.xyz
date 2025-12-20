package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.DeviceFingerprint;
import com.orbvpn.api.domain.enums.DeviceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceFingerprintRepository extends JpaRepository<DeviceFingerprint, Long> {

    List<DeviceFingerprint> findByIsActiveTrueOrderByPriorityDescConfidenceScoreDesc();

    List<DeviceFingerprint> findByMacPrefix(String macPrefix);

    List<DeviceFingerprint> findByDeviceType(DeviceType deviceType);

    List<DeviceFingerprint> findByMdnsServiceType(String mdnsServiceType);

    @Query("SELECT f FROM DeviceFingerprint f WHERE f.isActive = true AND " +
           "(f.macPrefix = :macPrefix OR " +
           "f.portSignature = :portSignature OR " +
           "f.mdnsServiceType = :mdnsService OR " +
           ":ssdpServer LIKE CONCAT('%', f.ssdpServerPattern, '%')) " +
           "ORDER BY f.priority DESC, f.confidenceScore DESC")
    List<DeviceFingerprint> findMatchingFingerprints(
        @Param("macPrefix") String macPrefix,
        @Param("portSignature") String portSignature,
        @Param("mdnsService") String mdnsService,
        @Param("ssdpServer") String ssdpServer
    );

    @Query("SELECT f FROM DeviceFingerprint f WHERE f.isActive = true AND " +
           "f.macPrefix = :macPrefix " +
           "ORDER BY f.confidenceScore DESC")
    List<DeviceFingerprint> findByMacPrefixActive(@Param("macPrefix") String macPrefix);

    @Query("SELECT f FROM DeviceFingerprint f WHERE f.isActive = true " +
           "ORDER BY f.matchCount DESC")
    List<DeviceFingerprint> findMostMatched(org.springframework.data.domain.Pageable pageable);

    @Modifying
    @Query("UPDATE DeviceFingerprint f SET f.matchCount = f.matchCount + 1 WHERE f.id = :id")
    void incrementMatchCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE DeviceFingerprint f SET f.confirmedByUsers = f.confirmedByUsers + 1, " +
           "f.confidenceScore = LEAST(100, f.confidenceScore + 1) WHERE f.id = :id")
    void confirmFingerprint(@Param("id") Long id);

    @Query("SELECT COUNT(f) FROM DeviceFingerprint f WHERE f.isActive = true")
    long countActive();

    @Query("SELECT f.deviceType, COUNT(f) FROM DeviceFingerprint f GROUP BY f.deviceType")
    List<Object[]> countByDeviceType();

    Optional<DeviceFingerprint> findByPortSignatureAndMacPrefix(String portSignature, String macPrefix);

    List<DeviceFingerprint> findByManufacturerContainingIgnoreCase(String manufacturer);
}
