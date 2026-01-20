package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.GlobalSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GlobalSettingsRepository extends JpaRepository<GlobalSettings, Long> {

    /**
     * Get the first (and only) global settings record.
     */
    Optional<GlobalSettings> findFirstByOrderByIdAsc();
}
