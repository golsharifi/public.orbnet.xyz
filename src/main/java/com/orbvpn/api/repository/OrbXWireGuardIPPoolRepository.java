// src/main/java/com/orbvpn/api/repository/OrbXWireGuardIPPoolRepository.java

package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.OrbXWireGuardIPPool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrbXWireGuardIPPoolRepository extends JpaRepository<OrbXWireGuardIPPool, Long> {

    Optional<OrbXWireGuardIPPool> findByOrbxServerId(Long orbxServerId);
}