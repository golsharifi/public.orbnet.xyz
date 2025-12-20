package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.ProcessedPayPalWebhook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProcessedPayPalWebhookRepository extends JpaRepository<ProcessedPayPalWebhook, Long> {

    /**
     * Check if a webhook event has already been processed
     */
    boolean existsByEventId(String eventId);

    /**
     * Find a processed webhook by event ID
     */
    Optional<ProcessedPayPalWebhook> findByEventId(String eventId);

    /**
     * Find webhooks by resource ID (order ID, subscription ID, etc.)
     */
    List<ProcessedPayPalWebhook> findByResourceIdOrderByProcessedAtDesc(String resourceId);

    /**
     * Find webhooks by event type
     */
    List<ProcessedPayPalWebhook> findByEventType(String eventType);

    /**
     * Find webhooks processed before a certain date (for cleanup)
     */
    List<ProcessedPayPalWebhook> findByProcessedAtBefore(LocalDateTime date);

    /**
     * Delete old processed webhooks (for cleanup job)
     */
    @Modifying
    @Query("DELETE FROM ProcessedPayPalWebhook p WHERE p.processedAt < :cutoffDate")
    int deleteByProcessedAtBefore(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Find failed webhooks for retry
     */
    List<ProcessedPayPalWebhook> findByStatusOrderByProcessedAtDesc(String status);

    /**
     * Count webhooks by status
     */
    long countByStatus(String status);
}
