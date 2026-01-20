package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.OrbMeshHomeDevice;
import com.orbvpn.api.domain.entity.OrbMeshNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrbMeshHomeDeviceRepository extends JpaRepository<OrbMeshHomeDevice, Long> {

    Optional<OrbMeshHomeDevice> findByNode(OrbMeshNode node);

    Optional<OrbMeshHomeDevice> findBySetupCode(String setupCode);

    List<OrbMeshHomeDevice> findByUser(User user);

    @Query("SELECT h FROM OrbMeshHomeDevice h WHERE h.user = :user AND h.node.online = true")
    List<OrbMeshHomeDevice> findOnlineByUser(@Param("user") User user);

    @Query("SELECT h FROM OrbMeshHomeDevice h WHERE h.isPublic = true AND h.node.online = true")
    List<OrbMeshHomeDevice> findPublicOnlineDevices();

    @Query("SELECT h FROM OrbMeshHomeDevice h WHERE h.ddnsEnabled = true")
    List<OrbMeshHomeDevice> findWithDdnsEnabled();

    @Query("SELECT COUNT(h) FROM OrbMeshHomeDevice h WHERE h.user = :user")
    int countByUser(@Param("user") User user);

    boolean existsBySetupCode(String setupCode);

    boolean existsByNode(OrbMeshNode node);
}
