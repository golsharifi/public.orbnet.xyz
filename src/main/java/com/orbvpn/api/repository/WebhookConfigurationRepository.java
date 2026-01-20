package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.WebhookConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface WebhookConfigurationRepository extends JpaRepository<WebhookConfiguration, Long> {
    @Query("SELECT w FROM WebhookConfiguration w WHERE w.active = :active")
    List<WebhookConfiguration> findByActive(boolean active);
}