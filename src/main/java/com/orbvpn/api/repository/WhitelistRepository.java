package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.Whitelist;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WhitelistRepository extends JpaRepository<Whitelist, Integer> {
    Whitelist findByIpAddress(String ipAddress);
    void deleteAll();
}
