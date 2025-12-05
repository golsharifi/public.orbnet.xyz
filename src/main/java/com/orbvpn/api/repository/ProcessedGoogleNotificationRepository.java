package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.ProcessedGoogleNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface ProcessedGoogleNotificationRepository extends JpaRepository<ProcessedGoogleNotification, String> {

    boolean existsByMessageId(String messageId);

    @Modifying
    @Query("DELETE FROM ProcessedGoogleNotification p WHERE p.processedAt < :cutoffTime")
    int deleteByProcessedAtBefore(@Param("cutoffTime") LocalDateTime cutoffTime);
}
