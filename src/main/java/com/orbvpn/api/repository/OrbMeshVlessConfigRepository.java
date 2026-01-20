package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.OrbMeshServer;
import com.orbvpn.api.domain.entity.OrbMeshVlessConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrbMeshVlessConfigRepository extends JpaRepository<OrbMeshVlessConfig, Long> {

    /**
     * Find VLESS config by user UUID and server
     */
    Optional<OrbMeshVlessConfig> findByUserUuidAndServer(String userUuid, OrbMeshServer server);

    /**
     * Find VLESS config by VLESS UUID (protocol UUID)
     */
    Optional<OrbMeshVlessConfig> findByVlessUuid(String vlessUuid);

    /**
     * Find all VLESS configs for a user
     */
    List<OrbMeshVlessConfig> findByUserUuid(String userUuid);

    /**
     * Find all VLESS configs for a server
     */
    List<OrbMeshVlessConfig> findByServer(OrbMeshServer server);

    /**
     * Find all active VLESS configs
     */
    List<OrbMeshVlessConfig> findByActiveTrue();

    /**
     * Find all active VLESS configs for a user
     */
    List<OrbMeshVlessConfig> findByUserUuidAndActiveTrue(String userUuid);

    /**
     * Find VLESS config by user UUID and server ID
     */
    Optional<OrbMeshVlessConfig> findByUserUuidAndServerId(String userUuid, Long serverId);
}
