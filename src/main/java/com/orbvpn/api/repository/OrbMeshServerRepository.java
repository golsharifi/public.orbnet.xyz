// src/main/java/com/orbvpn/api/repository/OrbMeshServerRepository.java

package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.OrbMeshServer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrbMeshServerRepository extends JpaRepository<OrbMeshServer, Long> {

        /**
         * Find server by name (display name)
         */
        Optional<OrbMeshServer> findByName(String name);

        /**
         * Find server by region (unique identifier)
         * Region examples: "eastus", "westus", "northeurope"
         */
        Optional<OrbMeshServer> findByRegion(String region);

        /**
         * Find server by hostname (DNS name)
         * Hostname examples: "orbmesh-eastus.orbvpn.com"
         */
        Optional<OrbMeshServer> findByHostname(String hostname);

        /**
         * Find all online and enabled servers (for users)
         */
        List<OrbMeshServer> findByOnlineTrueAndEnabledTrue();

        /**
         * Find online and enabled servers by country (for bridge selection)
         */
        List<OrbMeshServer> findByCountryAndOnlineTrueAndEnabledTrue(String country);

        /**
         * Find all enabled servers (for DNS server list, regardless of online status)
         * DNS servers should show even if temporarily marked offline
         */
        List<OrbMeshServer> findByEnabledTrue();

        /**
         * Find servers with available capacity
         */
        @Query("SELECT s FROM OrbMeshServer s WHERE s.online = true AND s.enabled = true AND s.currentConnections < s.maxConnections")
        List<OrbMeshServer> findServersWithCapacity();
}