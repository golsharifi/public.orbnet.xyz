package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.NotificationSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationSettingsRepository extends JpaRepository<NotificationSettings, Long> {
    @Query("SELECT ns FROM NotificationSettings ns WHERE ns.id = 1")
    NotificationSettings getSettings();
}
