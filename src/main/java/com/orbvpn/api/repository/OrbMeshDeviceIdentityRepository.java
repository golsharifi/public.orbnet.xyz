package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.OrbMeshDeviceIdentity;
import com.orbvpn.api.domain.enums.DeviceProvisioningStatus;
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
public interface OrbMeshDeviceIdentityRepository extends JpaRepository<OrbMeshDeviceIdentity, Long> {

    /**
     * Find device by device ID (unique identifier on the device)
     */
    Optional<OrbMeshDeviceIdentity> findByDeviceId(String deviceId);

    /**
     * Check if device ID exists
     */
    boolean existsByDeviceId(String deviceId);

    /**
     * Find all devices by status
     */
    List<OrbMeshDeviceIdentity> findByStatus(DeviceProvisioningStatus status);

    /**
     * Find devices by status (paginated)
     */
    Page<OrbMeshDeviceIdentity> findByStatus(DeviceProvisioningStatus status, Pageable pageable);

    /**
     * Find devices by manufacturing batch
     */
    List<OrbMeshDeviceIdentity> findByManufacturingBatch(String batch);

    /**
     * Find devices by model
     */
    List<OrbMeshDeviceIdentity> findByDeviceModel(String model);

    /**
     * Find devices owned by a user
     */
    List<OrbMeshDeviceIdentity> findByOwnerId(Long userId);

    /**
     * Find device by linked server ID
     */
    Optional<OrbMeshDeviceIdentity> findByServerId(Long serverId);

    /**
     * Count devices by status
     */
    long countByStatus(DeviceProvisioningStatus status);

    /**
     * Count devices by manufacturing batch and status
     */
    long countByManufacturingBatchAndStatus(String batch, DeviceProvisioningStatus status);

    /**
     * Find devices activated in a time range
     */
    @Query("SELECT d FROM OrbMeshDeviceIdentity d WHERE d.activatedAt BETWEEN :start AND :end")
    List<OrbMeshDeviceIdentity> findActivatedBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * Find devices not seen since a given time (potentially offline)
     */
    @Query("SELECT d FROM OrbMeshDeviceIdentity d WHERE d.status = 'ACTIVATED' AND d.lastSeenAt < :since")
    List<OrbMeshDeviceIdentity> findStaleDevices(@Param("since") LocalDateTime since);

    /**
     * Find devices with too many failed attempts (potential attack)
     */
    @Query("SELECT d FROM OrbMeshDeviceIdentity d WHERE d.failedAttempts >= :threshold")
    List<OrbMeshDeviceIdentity> findDevicesWithHighFailedAttempts(@Param("threshold") Integer threshold);

    /**
     * Search devices by partial device ID
     */
    @Query("SELECT d FROM OrbMeshDeviceIdentity d WHERE d.deviceId LIKE %:query%")
    Page<OrbMeshDeviceIdentity> searchByDeviceId(@Param("query") String query, Pageable pageable);

    /**
     * Get statistics by manufacturing batch
     */
    @Query("SELECT d.manufacturingBatch, d.status, COUNT(d) FROM OrbMeshDeviceIdentity d " +
           "GROUP BY d.manufacturingBatch, d.status ORDER BY d.manufacturingBatch")
    List<Object[]> getStatsByBatch();

    /**
     * Get statistics by device model
     */
    @Query("SELECT d.deviceModel, d.status, COUNT(d) FROM OrbMeshDeviceIdentity d " +
           "GROUP BY d.deviceModel, d.status ORDER BY d.deviceModel")
    List<Object[]> getStatsByModel();

    /**
     * Find devices by detected country
     */
    List<OrbMeshDeviceIdentity> findByDetectedCountry(String countryCode);

    /**
     * Count active devices by country
     */
    @Query("SELECT d.detectedCountry, COUNT(d) FROM OrbMeshDeviceIdentity d " +
           "WHERE d.status = 'ACTIVATED' GROUP BY d.detectedCountry")
    List<Object[]> countActiveByCountry();
}
