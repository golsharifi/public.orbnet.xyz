// src/main/java/com/orbvpn/api/repository/OrbXServerRepository.java

package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.OrbXServer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrbXServerRepository extends JpaRepository<OrbXServer, Long> {

        /**
         * Find server by name (display name)
         */
        Optional<OrbXServer> findByName(String name);

        /**
         * Find server by region (unique identifier)
         * Region examples: "eastus", "westus", "northeurope"
         */
        Optional<OrbXServer> findByRegion(String region);

        /**
         * Find server by hostname (DNS name)
         * Hostname examples: "orbx-eastus.orbvpn.com"
         */
        Optional<OrbXServer> findByHostname(String hostname);

        /**
         * Find all online and enabled servers (for users)
         */
        List<OrbXServer> findByOnlineTrueAndEnabledTrue();

        /**
         * Find servers with available capacity
         */
        @Query("SELECT s FROM OrbXServer s WHERE s.online = true AND s.enabled = true AND s.currentConnections < s.maxConnections")
        List<OrbXServer> findServersWithCapacity();
}