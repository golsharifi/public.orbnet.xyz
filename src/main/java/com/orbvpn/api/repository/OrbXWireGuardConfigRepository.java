package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.OrbXServer;
import com.orbvpn.api.domain.entity.OrbXWireGuardConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrbXWireGuardConfigRepository extends JpaRepository<OrbXWireGuardConfig, Long> {

    // ✅ Query by server relationship instead of serverId
    Optional<OrbXWireGuardConfig> findByUserUuidAndServer(String userUuid, OrbXServer server);

    List<OrbXWireGuardConfig> findByUserUuid(String userUuid);

    List<OrbXWireGuardConfig> findByServer(OrbXServer server);

    List<OrbXWireGuardConfig> findByActiveTrue();
}