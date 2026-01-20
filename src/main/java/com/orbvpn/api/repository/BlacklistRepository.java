package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.Blacklist;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlacklistRepository extends JpaRepository<Blacklist, Integer> {
    Blacklist findByIpAddress(String ipAddress);
    void deleteAll();
}
