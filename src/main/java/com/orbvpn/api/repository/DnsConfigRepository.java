package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.DnsConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DnsConfigRepository extends JpaRepository<DnsConfig, Long> {

    @Query("SELECT c FROM DnsConfig c ORDER BY c.id LIMIT 1")
    Optional<DnsConfig> findGlobalConfig();
}
