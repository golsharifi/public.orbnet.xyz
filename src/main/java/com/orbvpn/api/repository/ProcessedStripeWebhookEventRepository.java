package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.ProcessedStripeWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface ProcessedStripeWebhookEventRepository extends JpaRepository<ProcessedStripeWebhookEvent, String> {

    boolean existsByEventId(String eventId);

    @Modifying
    @Query("DELETE FROM ProcessedStripeWebhookEvent p WHERE p.processedAt < :cutoffTime")
    int deleteByProcessedAtBefore(@Param("cutoffTime") LocalDateTime cutoffTime);
}
