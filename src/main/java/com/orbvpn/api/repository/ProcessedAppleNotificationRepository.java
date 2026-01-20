package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.ProcessedAppleNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface ProcessedAppleNotificationRepository extends JpaRepository<ProcessedAppleNotification, String> {

    boolean existsByNotificationUUID(String notificationUUID);

    @Modifying
    @Query("DELETE FROM ProcessedAppleNotification p WHERE p.processedAt < :cutoffTime")
    int deleteByProcessedAtBefore(@Param("cutoffTime") LocalDateTime cutoffTime);
}
