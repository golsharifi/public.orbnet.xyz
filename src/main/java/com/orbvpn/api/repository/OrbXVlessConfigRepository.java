package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.OrbXServer;
import com.orbvpn.api.domain.entity.OrbXVlessConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrbXVlessConfigRepository extends JpaRepository<OrbXVlessConfig, Long> {

    /**
     * Find VLESS config by user UUID and server
     */
    Optional<OrbXVlessConfig> findByUserUuidAndServer(String userUuid, OrbXServer server);

    /**
     * Find VLESS config by VLESS UUID (protocol UUID)
     */
    Optional<OrbXVlessConfig> findByVlessUuid(String vlessUuid);

    /**
     * Find all VLESS configs for a user
     */
    List<OrbXVlessConfig> findByUserUuid(String userUuid);

    /**
     * Find all VLESS configs for a server
     */
    List<OrbXVlessConfig> findByServer(OrbXServer server);

    /**
     * Find all active VLESS configs
     */
    List<OrbXVlessConfig> findByActiveTrue();

    /**
     * Find all active VLESS configs for a user
     */
    List<OrbXVlessConfig> findByUserUuidAndActiveTrue(String userUuid);

    /**
     * Find VLESS config by user UUID and server ID
     */
    Optional<OrbXVlessConfig> findByUserUuidAndServerId(String userUuid, Long serverId);
}
