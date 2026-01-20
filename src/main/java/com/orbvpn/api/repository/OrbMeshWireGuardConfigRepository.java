package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.OrbMeshServer;
import com.orbvpn.api.domain.entity.OrbMeshWireGuardConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrbMeshWireGuardConfigRepository extends JpaRepository<OrbMeshWireGuardConfig, Long> {

    // ✅ Query by server relationship instead of serverId
    Optional<OrbMeshWireGuardConfig> findByUserUuidAndServer(String userUuid, OrbMeshServer server);

    List<OrbMeshWireGuardConfig> findByUserUuid(String userUuid);

    List<OrbMeshWireGuardConfig> findByServer(OrbMeshServer server);

    List<OrbMeshWireGuardConfig> findByActiveTrue();

    List<OrbMeshWireGuardConfig> findByUserUuidAndActiveTrue(String userUuid);
}