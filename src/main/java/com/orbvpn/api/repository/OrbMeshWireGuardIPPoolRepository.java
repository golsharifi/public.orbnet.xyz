// src/main/java/com/orbvpn/api/repository/OrbMeshWireGuardIPPoolRepository.java

package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.OrbMeshWireGuardIPPool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrbMeshWireGuardIPPoolRepository extends JpaRepository<OrbMeshWireGuardIPPool, Long> {

    Optional<OrbMeshWireGuardIPPool> findByOrbmeshServerId(Long orbmeshServerId);
}