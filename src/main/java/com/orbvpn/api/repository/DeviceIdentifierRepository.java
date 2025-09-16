package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.DeviceIdentifier;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface DeviceIdentifierRepository extends JpaRepository<DeviceIdentifier, Long> {
    Optional<DeviceIdentifier> findByDeviceIdAndPlatform(String deviceId, String platform);

    List<DeviceIdentifier> findByUserId(Long userId);

    boolean existsByDeviceIdAndPlatformAndIsBlockedTrue(String deviceId, String platform);
}