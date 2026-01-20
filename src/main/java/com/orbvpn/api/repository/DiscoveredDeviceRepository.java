package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.DiscoveredDevice;
import com.orbvpn.api.domain.entity.NetworkScan;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.enums.DeviceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiscoveredDeviceRepository extends JpaRepository<DiscoveredDevice, Long> {

    List<DiscoveredDevice> findByNetworkScan(NetworkScan networkScan);

    List<DiscoveredDevice> findByNetworkScanOrderByIpAddressAsc(NetworkScan networkScan);

    List<DiscoveredDevice> findByUserOrderByLastSeenAtDesc(User user);

    Page<DiscoveredDevice> findByUserOrderByLastSeenAtDesc(User user, Pageable pageable);

    @Query("SELECT dd FROM DiscoveredDevice dd WHERE dd.user = :user AND dd.macAddress = :macAddress ORDER BY dd.lastSeenAt DESC")
    List<DiscoveredDevice> findByUserAndMacAddress(@Param("user") User user, @Param("macAddress") String macAddress);

    @Query("SELECT dd FROM DiscoveredDevice dd WHERE dd.networkScan = :scan AND dd.isOnline = true ORDER BY dd.ipAddress")
    List<DiscoveredDevice> findOnlineDevices(@Param("scan") NetworkScan scan);

    @Query("SELECT dd FROM DiscoveredDevice dd WHERE dd.networkScan = :scan AND dd.vulnerabilityCount > 0 ORDER BY dd.vulnerabilityCount DESC")
    List<DiscoveredDevice> findVulnerableDevices(@Param("scan") NetworkScan scan);

    @Query("SELECT dd FROM DiscoveredDevice dd WHERE dd.networkScan = :scan AND dd.deviceType = :deviceType")
    List<DiscoveredDevice> findByNetworkScanAndDeviceType(@Param("scan") NetworkScan scan, @Param("deviceType") DeviceType deviceType);

    @Query("SELECT dd.deviceType, COUNT(dd) FROM DiscoveredDevice dd WHERE dd.networkScan = :scan GROUP BY dd.deviceType")
    List<Object[]> getDeviceTypeDistribution(@Param("scan") NetworkScan scan);

    @Query("SELECT DISTINCT dd.vendor FROM DiscoveredDevice dd WHERE dd.user = :user AND dd.vendor IS NOT NULL")
    List<String> findDistinctVendorsByUser(@Param("user") User user);

    // Admin queries
    @Query("SELECT dd.deviceType, COUNT(dd) FROM DiscoveredDevice dd GROUP BY dd.deviceType ORDER BY COUNT(dd) DESC")
    List<Object[]> getGlobalDeviceTypeDistribution();

    @Query("SELECT dd.vendor, COUNT(dd) FROM DiscoveredDevice dd WHERE dd.vendor IS NOT NULL GROUP BY dd.vendor ORDER BY COUNT(dd) DESC")
    List<Object[]> getGlobalVendorDistribution(Pageable pageable);
}
